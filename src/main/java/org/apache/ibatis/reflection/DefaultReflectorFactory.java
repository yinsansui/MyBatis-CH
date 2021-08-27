/**
 * Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认的 Reflector 工厂
 */
public class DefaultReflectorFactory implements ReflectorFactory {

  /**
   * 是否启用 class 缓存
   */
  private boolean classCacheEnabled = true;

  /**
   * 用于缓存 reflector
   */
  private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

  public DefaultReflectorFactory() {
  }

  /**
   * 获取是否启用了 reflector 缓存
   *
   * @return 是否启用
   */
  @Override
  public boolean isClassCacheEnabled() {
    return classCacheEnabled;
  }

  /**
   * 设置是否启用 reflector 缓存
   *
   * @param classCacheEnabled 是否启用缓存
   */
  @Override
  public void setClassCacheEnabled(boolean classCacheEnabled) {
    this.classCacheEnabled = classCacheEnabled;
  }

  /**
   * 通过 class，获取对应的 reflector
   *
   * @param type Class 类型
   * @return class 对应的 reflector
   */
  @Override
  public Reflector findForClass(Class<?> type) {
    // 如果启用缓存，则从 reflectorMap 中获取，否则每次都创建一个新的
    if (classCacheEnabled) {
      return reflectorMap.computeIfAbsent(type, Reflector::new);
    } else {
      return new Reflector(type);
    }
  }

}
