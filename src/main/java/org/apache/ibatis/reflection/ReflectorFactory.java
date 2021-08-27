/**
 * Copyright 2009-2015 the original author or authors.
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

/**
 * Reflector 工厂，用于创建 Reflector
 */
public interface ReflectorFactory {

  /**
   * 是否缓存 Class
   *
   * @return 是否缓存
   */
  boolean isClassCacheEnabled();

  /**
   * 设置是否启用 Class 缓存
   *
   * @param classCacheEnabled 是否启用缓存
   */
  void setClassCacheEnabled(boolean classCacheEnabled);

  /**
   * 找到通过 Class 找到对应的 Reflector
   *
   * @param type Class 类型
   * @return Reflector
   */
  Reflector findForClass(Class<?> type);
}
