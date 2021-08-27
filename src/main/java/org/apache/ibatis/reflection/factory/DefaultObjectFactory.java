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
package org.apache.ibatis.reflection.factory;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.Reflector;

/**
 * ObjectFactory 的默认实现类
 *
 * @author Clinton Begin
 */
public class DefaultObjectFactory implements ObjectFactory, Serializable {

  private static final long serialVersionUID = -8855120656740914948L;

  /**
   * 使用默认的无参构造器构造器创建一个对象
   *
   * @param type 对象所属的 Class
   * @param <T>  传入的 Class 的类型
   * @return T 类型的对象
   */
  @Override
  public <T> T create(Class<T> type) {
    return create(type, null, null);
  }

  /**
   * 用指定的类构造器创建一个对象
   *
   * @param type                对象所属的 Class
   * @param constructorArgTypes 构造器的参数类型列表
   * @param constructorArgs     传入构造器的参数列表
   * @param <T>                 传入的 Class 的类型
   * @return T 类型的对象
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes,
    List<Object> constructorArgs) {
    // 如果传入的一个接口类型，则要将接口类型转换为一个指定的实现类
    Class<?> classToCreate = resolveInterface(type);
    // 实例化 Class
    return (T) instantiateClass(classToCreate, constructorArgTypes, constructorArgs);
  }

  /**
   * 通过指定的构造器创建对象
   *
   * @param type                要实例化的 Class
   * @param constructorArgTypes 构造器的参数类型列表
   * @param constructorArgs     传入构造器的参数列表
   * @param <T>                 要实例的 Class 的类型
   * @return Class 的实例化对象
   */
  private <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes,
    List<Object> constructorArgs) {
    try {
      Constructor<T> constructor;
      if (constructorArgTypes == null || constructorArgs == null) {
        // 如果没有传入的构造器参数类型列表或者参数列表，则使用默认的无参构造器
        constructor = type.getDeclaredConstructor();
        try {
          // 使用默认的无参构造器实例化对象
          return constructor.newInstance();
        } catch (IllegalAccessException e) {
          if (Reflector.canControlMemberAccessible()) {
            constructor.setAccessible(true);
            return constructor.newInstance();
          } else {
            throw e;
          }
        }
      }

      // 获取指定的的类构造器
      constructor = type.getDeclaredConstructor(constructorArgTypes.toArray(new Class[0]));
      try {
        // 使用指定的类构造器创建对象
        return constructor.newInstance(constructorArgs.toArray(new Object[0]));
      } catch (IllegalAccessException e) {
        if (Reflector.canControlMemberAccessible()) {
          constructor.setAccessible(true);
          return constructor.newInstance(constructorArgs.toArray(new Object[0]));
        } else {
          throw e;
        }
      }
    } catch (Exception e) {
      String argTypes = Optional.ofNullable(constructorArgTypes).orElseGet(Collections::emptyList)
        .stream().map(Class::getSimpleName).collect(Collectors.joining(","));
      String argValues = Optional.ofNullable(constructorArgs).orElseGet(Collections::emptyList)
        .stream().map(String::valueOf).collect(Collectors.joining(","));
      throw new ReflectionException(
        "Error instantiating " + type + " with invalid types (" + argTypes + ") or values ("
          + argValues + "). Cause: " + e, e);
    }
  }

  /**
   * 如果传入的 Class 是一个接口类型（不能直接实例化），就要将该 Class 解析为一个可以直接实例化的实现类
   *
   * @param type 传入的 Class （可能无法直接实例化）
   * @return 可以直接实例化的 Class
   */
  protected Class<?> resolveInterface(Class<?> type) {
    Class<?> classToCreate;
    if (type == List.class || type == Collection.class || type == Iterable.class) {
      classToCreate = ArrayList.class;
    } else if (type == Map.class) {
      classToCreate = HashMap.class;
    } else if (type == SortedSet.class) {
      classToCreate = TreeSet.class;
    } else if (type == Set.class) {
      classToCreate = HashSet.class;
    } else {
      classToCreate = type;
    }
    return classToCreate;
  }

  /**
   * 传入一个 Class ，判断它是否是一个集合类型
   *
   * @param type 对象所属的 Class
   * @param <T>  Class 的类型
   * @return Class 是否是一个集合
   */
  @Override
  public <T> boolean isCollection(Class<T> type) {
    // 如果 type 是 Collection.class 本身或者是它的子类，那么就返回 true
    return Collection.class.isAssignableFrom(type);
  }

}
