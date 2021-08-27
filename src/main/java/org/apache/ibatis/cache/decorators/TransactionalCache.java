/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  // 被修饰的对象
  private final Cache delegate;
  // 事务提交后是否直接清理
  private boolean clearOnCommit;
  // 三级缓存
  private final Map<Object, Object> entriesToAddOnCommit;
  // 缓存查询未命中的数据
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // 从缓存中查询
    Object object = delegate.getObject(key);
    if (object == null) {
      // 记录缓存未命中
      entriesMissedInCache.add(key);
    }
    // 如果设置了提交立马清除，则直接返回 null
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  @Override
  public void putObject(Object key, Object object) {
    // 不直接放到被代理的 Cache 中，而是先暂存起来，等到事务提交后才真正写入到缓存中
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    // 提交时清除缓存
    clearOnCommit = true;
    // 清除事务缓存中所缓存的内容
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    // 如果执行过 clear，那么在真正提交的时候才让被代理 Cache 清除缓存
    // 以达到事务的隔离效果
    if (clearOnCommit) {
      delegate.clear();
    }
    // 真正提交事务
    flushPendingEntries();
    // 重置事务到初始状态
    reset();
  }

  public void rollback() {
    // 释放锁
    unlockMissedEntries();
    // 重置事务到初始状态
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    // 1. 刷新事务缓存到被代理对象的 Cache 中
    // 即将在这里才将 putObject 的指令真正的执行
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }

    // 2. 将缓存未命中的同样要执行一次 put 指令
    // 因为如果缓存被 BlockingCache 修饰过的话，在返回 null 的同时会阻塞后面
    // 对该数据的请求，然后在事务提交后或回滚后就需要将锁全部都进行释放
    for (Object entry : entriesMissedInCache) {
      // 如果已经执行过 putObject 就不需要再执行一次了
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    // 和 flushPendingEntries 第二步的作用也是一样的
    // 不过这里是通过调用 removeObject 来达到释放锁的效果
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
