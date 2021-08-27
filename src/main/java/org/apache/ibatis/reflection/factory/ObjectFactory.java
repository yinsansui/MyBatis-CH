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
package org.apache.ibatis.reflection.factory;

import java.util.List;
import java.util.Properties;

/**
 * ObjectFactory 用于创建所有需要的对象
 *
 * @author Clinton Begin
 */
public interface ObjectFactory {

  /**
   * 设置配置的属性
   *
   * @param properties 配置属性
   */
  default void setProperties(Properties properties) {
    // NOP
  }

  /**
   * 使用默认的无参构造器构造器创建一个对象
   *
   * @param <T>  传入的 Class 的类型
   * @param type 对象所属的 Class
   * @return T 类型的对象
   */
  <T> T create(Class<T> type);

  /**
   * 用指定的类构造器创建一个对象
   *
   * @param <T>                 传入的 Class 的类型
   * @param type                对象所属的 Class
   * @param constructorArgTypes 构造器的参数类型列表
   * @param constructorArgs     传入构造器的参数列表
   * @return T 类型的对象
   */
  <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

  /**
   * 传入一个 Class ，判断它是否是一个集合类型
   *
   * @param <T>  传入的 Class 的类型
   * @param type 对象所属的 Class
   * @return Class 类型是否一个集合
   * @since 3.1.0
   */
  <T> boolean isCollection(Class<T> type);

}
