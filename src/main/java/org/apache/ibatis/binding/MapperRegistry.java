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
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * mapper 文件的注册表
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {

  /**
   * Mybatis 的配置文件
   */
  private final Configuration config;
  /**
   * 保存所有已知的 MapperProxyFactory 可以理解为 Java 映射接口
   */
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  /**
   * 通过 Class(接口类型) 获取对应的代理实现类
   */
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    // 如果找不到直接抛出异常
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    // 实例化对象
    try {
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  /**
   * Class(接口) 是否有对应的代理实现类
   */
  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * 为 Class(接口) 创建其对应的代理实现类
   */
  public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) {
      // 只添加 interface
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      // 是否加载完成的标志，
      boolean loadCompleted = false;
      try {
        // 先把坑占好，防止重复加载
        /*
          Mapper 代表的是 Java 文件，XMLMapper 代表的是 Mybatis 的映射文件

          可能会出现重复加载的情况一：
          通过 Mapper 路径找到了 XMLMapper，通过解析 XMLMapper 找到了 Mapper，
          然后可能又要去加载这个 Class，为了防止这种情况，所以需要先创建好对应的 MapperProxyFactory。
          例如：
            Mapper = com.yinxy.demo.UserMapper
            XMLMapper 文件路径必须为：com/yinxy/demo/UserMapper.xml

          可能会出现重复加载的情况二：
          通过 XMLMapper 找到了 Mapper，通过解析 Mapper 又找到了 XMLMapper。
          例如：
            XMLMapper 文件路径任意
            Mapper：XMLMapper 的 <mapper> 标签的 namespace 属性
         */
        knownMappers.put(type, new MapperProxyFactory<>(type));
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        // 加载并解析
        parser.parse();
        loadCompleted = true;
      } finally {
        // 如果由于报错没有加载完成，那么就要将预先生成好的 MapperProxyFactory 删掉
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * Gets the mappers.
   *
   * @return the mappers
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * Adds the mappers.
   *
   * @param packageName
   *          the package name
   * @param superType
   *          the super type
   * @since 3.2.2
   */
  public void addMappers(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {
      addMapper(mapperClass);
    }
  }

  /**
   * Adds the mappers.
   *
   * @param packageName
   *          the package name
   * @since 3.2.2
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

}
