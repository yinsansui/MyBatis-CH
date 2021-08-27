/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.lang.reflect.Array;
import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;

/**
 * 结果提取器
 *
 * @author Andrew Gustafson
 */
public class ResultExtractor {
  // MyBatis 全局配置信息
  private final Configuration configuration;
  // 对象工厂
  private final ObjectFactory objectFactory;

  public ResultExtractor(Configuration configuration, ObjectFactory objectFactory) {
    this.configuration = configuration;
    this.objectFactory = objectFactory;
  }

  // 从结果列表中提取出对象，其实就是将查询结果适配到目标类型中
  public Object extractObjectFromList(List<Object> list, Class<?> targetType) {
    Object value = null;
    if (targetType != null && targetType.isAssignableFrom(list.getClass())) {// 如果 list 可以直接赋值给目标类型
      value = list;
    } else if (targetType != null && objectFactory.isCollection(targetType)) {// 如果目标类型是集合
      value = objectFactory.create(targetType);
      MetaObject metaObject = configuration.newMetaObject(value);
      metaObject.addAll(list);
    } else if (targetType != null && targetType.isArray()) {// 如果目标类型是数组
      Class<?> arrayComponentType = targetType.getComponentType();
      Object array = Array.newInstance(arrayComponentType, list.size());
      if (arrayComponentType.isPrimitive()) {
        for (int i = 0; i < list.size(); i++) {
          Array.set(array, i, list.get(i));
        }
        value = array;
      } else {
        value = list.toArray((Object[])array);
      }
    } else {// 其他情况
      if (list != null && list.size() > 1) {// 如果有多条记录，但是目标类型只能存储一条记录，所以抛出异常
        throw new ExecutorException("Statement returned more than one row, where no more than one was expected.");
      } else if (list != null && list.size() == 1) {// 只有一条查询结果
        value = list.get(0);
      }
    }
    return value;
  }
}
