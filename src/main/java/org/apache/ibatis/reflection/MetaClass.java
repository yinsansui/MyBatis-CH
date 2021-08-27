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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 元类
 *
 * @author Clinton Begin
 */
public class MetaClass {

  /**
   * 反射器工厂
   */
  private final ReflectorFactory reflectorFactory;

  /**
   * 反射器
   */
  private final Reflector reflector;

  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 通过属性名称，找到对应的类型的元类
   *
   * @param name 属性名称
   * @return 反射器
   */
  public MetaClass metaClassForProperty(String name) {
    // 通过属性找到对应的类型
    Class<?> propType = reflector.getGetterType(name);
    // 获取该类型的元类
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 规范化属性。例如：name = "user.idcard.Number"，规范后的结果可能就是 "user.idCard.number"
   *
   * @param name 属性
   * @return 规范化后的属性
   */
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 规范化属性。例如：name = "user.idcard.Number"，规范后的结果可能就是 "user.idCard.number"
   *
   * @param name                属性
   * @param useCamelCaseMapping 是否使用了驼峰命名法
   * @return 规范化后的属性
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    // 如果使用了驼峰命令法，则需要去掉下划线再进行规范化
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  /**
   * 获取所有可读的属性名称
   *
   * @return 所有可读的属性名称
   */
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  /**
   * 获取所有可写的属性名称
   *
   * @return 所有可写的属性名称
   */
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  /**
   * 通过属性名称获取 set 方法的请求参数类型
   *
   * @param name 属性名称
   * @return set 方法的请求参数类型
   */
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果有子属性的话，则一直向下寻找
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      // 没有子属性，则获取当前属性的 set 方法的请求参数类型
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 通过属性名称获取 get 方法的请求参数类型
   *
   * @param name 属性名称
   * @return get 方法的返回参数类型
   */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果有子属性的话，则一直向下寻找
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 通过属性分词器获取属性对应 get 方法的返回类型
   *
   * @param prop 属性分词器
   * @return 属性对应 get 方法的返回类型
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 通过 name 获取类型
    Class<?> type = reflector.getGetterType(prop.getName());
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 如果属性附带了下标，并且是一个集合类型
      Type returnType = getGenericGetterType(prop.getName());
      if (returnType instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  /**
   * 通过属性名称获取 get 方法的通用返回类型
   *
   * @param propertyName 属性名称
   * @return 名称对应的 get 方法的通用返回类型
   */
  private Type getGenericGetterType(String propertyName) {
    try {
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 根据 Invoker 的实现类，来决定怎么获取对应的返回类型
      if (invoker instanceof MethodInvoker) {
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        declaredMethod.setAccessible(true);
        Method method = (Method) declaredMethod.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        Field field = (Field) declaredField.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  /**
   * 属性是有否对应的 set 方法
   *
   * @param name 属性名称
   * @return 是否存在对应的 set 方法
   */
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果有子属性，则继续向下查找
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  /**
   * 属性是否有对应的 get 方法
   *
   * @param name 属性名称
   * @return 是否有对应的 get 方法
   */
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果有子属性，则继续向下查找
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  /**
   * 通过属性名称获取 getInvoker
   *
   * @param name 属性名称
   * @return getInvoker
   */
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  /**
   * 通过属性名称获取 setInvoker
   *
   * @param name 属性名称
   * @return setInvoker
   */
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * 构建属性，用于规范化属性名称，并不是真正的构建。 例如：原来的 name = "user.idcard.Number"，规范后的结果可能就是 "user.idCard.number"
   *
   * @param name    属性名
   * @param builder 属性名构建结果
   * @return 规范化后的属性名
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果属性有子属性的话，即 user.name 这种形式，name 就是子属性
      // 获得规范化后的属性名称
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        // 通过属性名找到对应的元类
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 让元类来继续构建剩下的属性
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      // 如果属性没有子属性
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  /**
   * 是否有默认的构造方法
   *
   * @return 是否有默认的构造方法
   */
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
