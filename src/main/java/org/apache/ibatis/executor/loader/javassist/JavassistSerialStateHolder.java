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
package org.apache.ibatis.executor.loader.javassist;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.executor.loader.AbstractSerialStateHolder;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.reflection.factory.ObjectFactory;

/**
 * 它会保存关键信息，保证在经历了序列化和反序列化的后以后也有懒加载功能
 *
 * @author Eduardo Macarron
 */
class JavassistSerialStateHolder extends AbstractSerialStateHolder {

  private static final long serialVersionUID = 8940388717901644661L;

  public JavassistSerialStateHolder() {
  }

  /**
   * 构造方法
   *
   * @param userBean            要序列化的原始对象
   * @param unloadedProperties  未加载的属性
   * @param objectFactory       对象工厂
   * @param constructorArgTypes 构造方法参数类型列表
   * @param constructorArgs     构造方法参数列表
   */
  public JavassistSerialStateHolder(
    final Object userBean,
    final Map<String, ResultLoaderMap.LoadPair> unloadedProperties,
    final ObjectFactory objectFactory,
    List<Class<?>> constructorArgTypes,
    List<Object> constructorArgs) {
    super(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
  }

  /**
   * 构建反序列化代理
   *
   * @param target              被代理对象
   * @param unloadedProperties  未懒加载的属性
   * @param objectFactory       对象工厂
   * @param constructorArgTypes 构造方法参数类型列表
   * @param constructorArgs     构造方法参数列表
   * @return 代理对象
   */
  @Override
  protected Object createDeserializationProxy(Object target,
    Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
    List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return new JavassistProxyFactory()
      .createDeserializationProxy(target, unloadedProperties, objectFactory, constructorArgTypes,
        constructorArgs);
  }
}
