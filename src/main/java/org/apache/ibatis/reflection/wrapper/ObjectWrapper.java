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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装器
 *
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 根据属性分词器获取属性值
   *
   * @param prop 属性分词器
   * @return 对象包装器
   */
  Object get(PropertyTokenizer prop);

  /**
   * 根据属性分词器设置属性值
   *
   * @param prop  属性分词器
   * @param value 要设置的值
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 通过不规范的属性名称查找规范化的属性名
   *
   * @param name                不规范的属性名
   * @param useCamelCaseMapping 是否是驼峰命名法
   * @return 规范化的属性名
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 获取当前对象所有可读的属性名称
   *
   * @return 当前对象所有可读的属性名称
   */
  String[] getGetterNames();

  /**
   * 获取当前对象所有可写的属性名称
   *
   * @return 可写的属性名称
   */
  String[] getSetterNames();

  /**
   * 通过属性名称获取对应 set 方法的入参类型
   *
   * @param name 属性名称
   * @return set 方法请求参数类型
   */
  Class<?> getSetterType(String name);

  /**
   * 通过属性名称获取对应 get 方法的返回参数类型
   *
   * @param name 属性名称
   * @return get 方法返回参数类型
   */
  Class<?> getGetterType(String name);

  /**
   * 通过属性名称查询是否有对应的 set 方法
   *
   * @param name 属性名称
   * @return 是否有 set 方法，即是否可写
   */
  boolean hasSetter(String name);

  /**
   * 通过属性名称查询是否有对应的 get 方法
   *
   * @param name 属性名称
   * @return 是否有 get 方法，即是否可读
   */
  boolean hasGetter(String name);

  /**
   * 实例化属性值
   *
   * @param name          属性名称
   * @param prop          属性分词器
   * @param objectFactory 对象工厂
   * @return 元属性
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop,
    ObjectFactory objectFactory);

  /**
   * 当前对象是否是集合类型
   *
   * @return 是否是集合类型
   */
  boolean isCollection();

  /**
   * 新增元素，只有在当前对象是一个集合的时候才可以新增
   *
   * @param element 元素
   */
  void add(Object element);

  /**
   * 新增元素列表，只有在当前对象是一个集合的时候才可以新
   *
   * @param element 元素
   * @param <E>     元素类型
   */
  <E> void addAll(List<E> element);

}
