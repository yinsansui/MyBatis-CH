/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator.
 *
 * 最近最少使用缓存
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  private final Cache delegate;
  // 用于实现 LRU 的数据结构
  private Map<Object, Object> keyMap;
  // 待删除的 key
  private Object eldestKey;

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
    // 利用 LinkedHashMap 实现的 LRU
    // 第三个参数为排序规则，true 则是通过访问顺序排序，false 则是通过查询顺序排序
    // 由于要实现自动删除最近最少使用的，所以要重写 removeEldestEntry 如果返回 true 就会自动删除最近使用最少的一条记录
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        // 如果大于了 size 的话，则需要删除最近最少使用的 key
        // 但是这里的删除只是删除的 LRU 记录的 key，我们还需要同步删除被修饰对象中
        // 对应的 key。所以这里会先暂存一下。
        boolean tooBig = size() > size;
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    keyMap.get(key); // touch
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  /**
   * 在每次调用 putObject 的时候，就会执行这个方法，保证在 keyMap 中删除的记录，会同步从代理类中删除
   * @param key 缓存的 key
   */
  private void cycleKeyList(Object key) {
    // 同步往 keyMap 中存放值的 key
    keyMap.put(key, key);
    // 如果在放入 keyMap 时删除了最近最少使用的 key 则需要让 delegate 也删除这个 key
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
