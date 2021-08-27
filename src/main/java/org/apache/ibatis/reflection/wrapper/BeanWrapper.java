/**
 * Copyright 2009-2017 the original author or authors.
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

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class BeanWrapper extends BaseWrapper {

  /**
   * 要包装的对象
   */
  private final Object object;

  /**
   * 元类
   */
  private final MetaClass metaClass;

  public BeanWrapper(MetaObject metaObject, Object object) {
    super(metaObject);
    this.object = object;
    this.metaClass = MetaClass.forClass(object.getClass(), metaObject.getReflectorFactory());
  }

  /**
   * 根据属性分词器获取属性值
   *
   * @param prop 属性分词器
   * @return 属性值
   */
  @Override
  public Object get(PropertyTokenizer prop) {
    if (prop.getIndex() != null) {
      // 如果有下标，说明当前对象是一个集合
      Object collection = resolveCollection(prop, object);
      // 从集合中获取属性值
      return getCollectionValue(prop, collection);
    } else {
      // 获取 bean 中属性
      return getBeanProperty(prop, object);
    }
  }

  /**
   * 根据属性分词器设置属性值
   *
   * @param prop  属性分词器
   * @param value 要设置的值
   */
  @Override
  public void set(PropertyTokenizer prop, Object value) {
    if (prop.getIndex() != null) {
      // 如果有下标，说明当前对象是一个集合
      Object collection = resolveCollection(prop, object);
      // 设置集合的值
      setCollectionValue(prop, collection, value);
    } else {
      // 如果不是一个集合，则直接设置 bean 中属性值
      setBeanProperty(prop, object, value);
    }
  }

  /**
   * 查找属性值，也是规范化属性值
   *
   * @param name                不规范的属性名
   * @param useCamelCaseMapping 是否是驼峰命名法
   * @return 规范化后的属性值
   */
  @Override
  public String findProperty(String name, boolean useCamelCaseMapping) {
    return metaClass.findProperty(name, useCamelCaseMapping);
  }

  /**
   * 获取当前对象所有可读的属性名称
   *
   * @return 当前对象所有可读的属性名称
   */
  @Override
  public String[] getGetterNames() {
    return metaClass.getGetterNames();
  }

  /**
   * 获取当前对象所有可写的属性名称
   *
   * @return 可写的属性名称
   */
  @Override
  public String[] getSetterNames() {
    return metaClass.getSetterNames();
  }

  /**
   * 通过属性名称获取对应 set 方法的入参类型
   *
   * @param name 属性名称
   * @return set 方法请求参数类型
   */
  @Override
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 通过属性名称，找到对应的元对象
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        // 如果元对象没有找到，那么就从元类中获取 set 方法的请求参数
        return metaClass.getSetterType(name);
      } else {
        // 如果属性对应的元对象找到了，则继续向下查找
        return metaValue.getSetterType(prop.getChildren());
      }
    } else {
      // 如果没有子属性，那么就获取该属性的 set 方法的请求参数
      return metaClass.getSetterType(name);
    }
  }

  /**
   * 通过属性名称获取对应 get 方法的返回参数
   *
   * @param name 属性名称
   * @return get 方法返回参数类型
   */
  @Override
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 通过属性名称，找到对应的元对象
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        // 如果元对象没有找到，那么就从元类中获取 get 方法的返回参数
        return metaClass.getGetterType(name);
      } else {
        // 如果属性对应的元对象找到了，则继续向下查找
        return metaValue.getGetterType(prop.getChildren());
      }
    } else {
      // 如果没有子属性，那么就获取该属性的 get 方法的返回参数
      return metaClass.getGetterType(name);
    }
  }

  /**
   * 属性是否可写
   *
   * @param name 属性名称
   * @return 是否可写
   */
  @Override
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果分词器找到属性还有子属性，则向下查找
      if (metaClass.hasSetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasSetter(name);
        } else {
          return metaValue.hasSetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      // 如果分词器没有找到属性还有子属性，则直接查找当前元类该属性是否可写
      return metaClass.hasSetter(name);
    }
  }

  /**
   * 属性是否可读
   *
   * @param name 属性名称
   * @return 是否可读
   */
  @Override
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果分词器找到属性还有子属性，则向下查找
      if (metaClass.hasGetter(prop.getIndexedName())) {
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasGetter(name);
        } else {
          return metaValue.hasGetter(prop.getChildren());
        }
      } else {
        return false;
      }
    } else {
      // 如果分词器没有找到属性还有子属性，则直接查找当前元类该属性是否可读
      return metaClass.hasGetter(name);
    }
  }

  /**
   * 实例化属性值，得到元对象
   *
   * @param name          属性名称
   * @param prop          属性分词器
   * @param objectFactory 对象工厂
   * @return 元对象
   */
  @Override
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop,
    ObjectFactory objectFactory) {
    MetaObject metaValue;
    // 获取属性对应的类型
    Class<?> type = getSetterType(prop.getName());
    try {
      // 实例化对象
      Object newObject = objectFactory.create(type);
      // 将实例化对象包装为元对象
      metaValue = MetaObject.forObject(newObject, metaObject.getObjectFactory(),
        metaObject.getObjectWrapperFactory(), metaObject.getReflectorFactory());
      set(prop, newObject);
    } catch (Exception e) {
      throw new ReflectionException("Cannot set value of property '" + name + "' because '" + name
        + "' is null and cannot be instantiated on instance of " + type.getName() + ". Cause:" + e
        .toString(), e);
    }
    return metaValue;
  }

  /**
   * 通过属性分词器从对象中获取该属性的值
   *
   * @param prop   属性分词器
   * @param object 对象
   * @return 该属性的值
   */
  private Object getBeanProperty(PropertyTokenizer prop, Object object) {
    try {
      // 通过属性名获取 getInvoker
      Invoker method = metaClass.getGetInvoker(prop.getName());
      try {
        // 调用 get 方法，获取属性值
        return method.invoke(object, NO_ARGUMENTS);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new ReflectionException(
        "Could not get property '" + prop.getName() + "' from " + object.getClass() + ".  Cause: "
          + t.toString(), t);
    }
  }

  /**
   * 通过属性分词器、对象和属性值
   *
   * @param prop   属性分词器
   * @param object 要设置属性值的对象
   * @param value  要设置的值
   */
  private void setBeanProperty(PropertyTokenizer prop, Object object, Object value) {
    try {
      // 通过属性名获取 setInvoker
      Invoker method = metaClass.getSetInvoker(prop.getName());
      Object[] params = {value};
      try {
        // 调用 set 方法，设置属性值
        method.invoke(object, params);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (Throwable t) {
      throw new ReflectionException(
        "Could not set property '" + prop.getName() + "' of '" + object.getClass()
          + "' with value '" + value + "' Cause: " + t.toString(), t);
    }
  }

  /**
   * 当前对象是否是集合，BeanWrapper 不是集合，所以返回 false
   * @return 是否是集合
   */
  @Override
  public boolean isCollection() {
    return false;
  }

  @Override
  public void add(Object element) {
    // 由于不是集合，所以不能新增
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> void addAll(List<E> list) {
    // 由于不是集合，所以不能新增
    throw new UnsupportedOperationException();
  }

}
