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

import java.util.Arrays;

/**
 * 提供数组对象的相关工具 hashCode、equals、toString
 */
public class ArrayUtil {

  /**
   * 返回一个对象的 hashCode
   *
   * @param obj 要获取 hashCode 的对象，可能是一个数组对象，可以是 null
   * @return 一个对象的 hashCode，或者 0 (obj == null)
   */
  public static int hashCode(Object obj) {
    if (obj == null) {
      return 0;
    }

    final Class<?> clazz = obj.getClass();
    if (!clazz.isArray()) {
      // 如果不是数组，则直接调用对象的 hashCode 方法
      return obj.hashCode();
    }

    // 如果 Class 是数组则得到成员的类型
    final Class<?> componentType = clazz.getComponentType();
    // 将 obj 强转为对应的数组类型，然后再获取 hashCode
    if (long.class.equals(componentType)) {
      return Arrays.hashCode((long[]) obj);
    } else if (int.class.equals(componentType)) {
      return Arrays.hashCode((int[]) obj);
    } else if (short.class.equals(componentType)) {
      return Arrays.hashCode((short[]) obj);
    } else if (char.class.equals(componentType)) {
      return Arrays.hashCode((char[]) obj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.hashCode((byte[]) obj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.hashCode((boolean[]) obj);
    } else if (float.class.equals(componentType)) {
      return Arrays.hashCode((float[]) obj);
    } else if (double.class.equals(componentType)) {
      return Arrays.hashCode((double[]) obj);
    } else {
      return Arrays.hashCode((Object[]) obj);
    }
  }

  /**
   * 比较两个对象. 返回 <code>true</code> 有以下几种可能
   * <ul>
   * <li>{@code thisObj} 和 {@code thatObj} 都是 <code>null</code></li>
   * <li>{@code thisObj} 和 {@code thatObj} 都是相同的类型，并且
   * {@link Object#equals(Object)} 返回的是 <code>true</code></li>
   * <li>{@code thisObj} 和 {@code thatObj} 都是数组，成员类型是一样的，并且
   * {@link Arrays} 的 equals() 方法返回的 <code>true</code></li>
   * </ul>
   *
   * @param thisObj
   *          The left hand object to compare. May be an array or <code>null</code>
   * @param thatObj
   *          The right hand object to compare. May be an array or <code>null</code>
   * @return <code>true</code> if two objects are equal; <code>false</code> otherwise.
   */
  public static boolean equals(Object thisObj, Object thatObj) {
    // thisObj 和 thatObj 都是 null 才是 true，只有任意一个是 null 则是 false
    if (thisObj == null) {
      return thatObj == null;
    } else if (thatObj == null) {
      return false;
    }

    final Class<?> clazz = thisObj.getClass();
    if (!clazz.equals(thatObj.getClass())) {
      // 如果类型不一样则返回 false
      return false;
    }
    if (!clazz.isArray()) {
      // 如果不是数组，则直接调用 Object.equals(Object) 方法进行比较
      return thisObj.equals(thatObj);
    }

    // 如果是数组，则通过 Arrays.equals() 进行比较
    final Class<?> componentType = clazz.getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.equals((long[]) thisObj, (long[]) thatObj);
    } else if (int.class.equals(componentType)) {
      return Arrays.equals((int[]) thisObj, (int[]) thatObj);
    } else if (short.class.equals(componentType)) {
      return Arrays.equals((short[]) thisObj, (short[]) thatObj);
    } else if (char.class.equals(componentType)) {
      return Arrays.equals((char[]) thisObj, (char[]) thatObj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.equals((byte[]) thisObj, (byte[]) thatObj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.equals((boolean[]) thisObj, (boolean[]) thatObj);
    } else if (float.class.equals(componentType)) {
      return Arrays.equals((float[]) thisObj, (float[]) thatObj);
    } else if (double.class.equals(componentType)) {
      return Arrays.equals((double[]) thisObj, (double[]) thatObj);
    } else {
      return Arrays.equals((Object[]) thisObj, (Object[]) thatObj);
    }
  }

  /**
   * 如果 {@code obj} 是一个数组, 则调用用 {@link Arrays} 的 toString() 方法。如果不是则调用
   * {@link Object#toString()}。如果 {@code obj} 是 <code>null</code>，就返回 "null"。
   *
   * @param obj 一个可能是数组或者 <code>null</code> 的对象
   * @return 根据规则将 obj 转字符串
   */
  public static String toString(Object obj) {
    if (obj == null) {
      // 如果是 null 则直接返回 'null'
      return "null";
    }

    final Class<?> clazz = obj.getClass();
    if (!clazz.isArray()) {
      // 如果不是数组，则调用 Object.equals() 方法
      return obj.toString();
    }

    // 如果是数组，则先将 obj 转换为对应的类型，再调用 Arrays.equals() 的方法
    final Class<?> componentType = obj.getClass().getComponentType();
    if (long.class.equals(componentType)) {
      return Arrays.toString((long[]) obj);
    } else if (int.class.equals(componentType)) {
      return Arrays.toString((int[]) obj);
    } else if (short.class.equals(componentType)) {
      return Arrays.toString((short[]) obj);
    } else if (char.class.equals(componentType)) {
      return Arrays.toString((char[]) obj);
    } else if (byte.class.equals(componentType)) {
      return Arrays.toString((byte[]) obj);
    } else if (boolean.class.equals(componentType)) {
      return Arrays.toString((boolean[]) obj);
    } else if (float.class.equals(componentType)) {
      return Arrays.toString((float[]) obj);
    } else if (double.class.equals(componentType)) {
      return Arrays.toString((double[]) obj);
    } else {
      return Arrays.toString((Object[]) obj);
    }
  }

}
