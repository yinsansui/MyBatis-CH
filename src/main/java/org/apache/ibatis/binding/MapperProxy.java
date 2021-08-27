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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -4724728412955527868L;
  // 只允许以下作用域的方法能执行反射，利用二进制的每一位代表一种修饰符，可以节省空间
  private static final int ALLOWED_MODES =
    MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
      | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;
  // JDK8 中只有 Lookup(Class, int)
  private static final Constructor<Lookup> lookupConstructor;
  // 在 JDK9 中才有 privateLookupIn(Class, Lookup)
  private static final Method privateLookupInMethod;
  // 会话
  private final SqlSession sqlSession;
  // 被代理对象
  private final Class<T> mapperInterface;
  // Java 映射文件中的方法及其对应的调用器，对象是由 MapperProxyFactory 创建的，生成的每个代理对象都是其的引用
  // 所以，所有的代理对象都共享这些内容
  private final Map<Method, MapperMethodInvoker> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface,
    Map<Method, MapperMethodInvoker> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  static {
    // 判断当前 JDK 版本是 JDK8 还是 JDK9
    Method privateLookupIn;
    try {
      privateLookupIn = MethodHandles.class
        .getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    } catch (NoSuchMethodException e) {
      privateLookupIn = null;
    }
    privateLookupInMethod = privateLookupIn;

    Constructor<Lookup> lookup = null;
    if (privateLookupInMethod == null) {
      // JDK 1.8
      try {
        lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        lookup.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
          "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
          e);
      } catch (Exception e) {
        lookup = null;
      }
    }
    lookupConstructor = lookup;
  }

  /**
   * 当调用映射接口中的方法时，就会调用以下方法
   *
   * @param proxy  代理对象
   * @param method 要调用的方法
   * @param args   请求参数
   * @return 调用结果
   * @throws Throwable 未知
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      if (Object.class.equals(method.getDeclaringClass())) {
        // 如果调用的时 Object 类中声明的方法，直接反射调用
        return method.invoke(this, args);
      } else {
        return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * 通过 method 获取已缓存的 Invoker 方法
   *
   * @param method 要调用的 method
   * @return method 对应的 Invoker
   */
  private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
    try {
      MapperMethodInvoker invoker = methodCache.get(method);
      if (invoker != null) {
        // 如果在缓存中找到了对应的 invoker 方法，那么则直接返回
        return invoker;
      }

      // 如果没有缓存对应的 invoker 方法，则构建并缓存起来
      return methodCache.computeIfAbsent(method, m -> {
        if (m.isDefault()) {
          // 如果是一个默认方法
          try {
            if (privateLookupInMethod == null) {
              return new DefaultMethodInvoker(getMethodHandleJava8(method));
            } else {
              return new DefaultMethodInvoker(getMethodHandleJava9(method));
            }
          } catch (IllegalAccessException | InstantiationException | InvocationTargetException
            | NoSuchMethodException e) {
            throw new RuntimeException(e);
          }
        } else {
          // 如果不是默认方法，说明是一个需要映射的方法，即我们常用的 userMapper.getById 这种。
          return new PlainMethodInvoker(
            new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
        }
      });
    } catch (RuntimeException re) {
      Throwable cause = re.getCause();
      throw cause == null ? re : cause;
    }
  }

  private MethodHandle getMethodHandleJava9(Method method)
    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup()))
      .findSpecial(
        declaringClass, method.getName(),
        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
        declaringClass);
  }

  private MethodHandle getMethodHandleJava8(Method method)
    throws IllegalAccessException, InstantiationException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES)
      .unreflectSpecial(method, declaringClass);
  }

  /**
   * Mapper 中方法的调用
   */
  interface MapperMethodInvoker {

    /**
     * 调用方法
     *
     * @param proxy      要执行方法的对象
     * @param method     要执行的方法
     * @param args       请求参数
     * @param sqlSession sql 会话
     * @return 结果
     * @throws Throwable
     */
    Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession)
      throws Throwable;
  }

  /**
   * 用来执行 Mapper 中的映射方法
   */
  private static class PlainMethodInvoker implements MapperMethodInvoker {

    private final MapperMethod mapperMethod;

    public PlainMethodInvoker(MapperMethod mapperMethod) {
      super();
      this.mapperMethod = mapperMethod;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession)
      throws Throwable {
      return mapperMethod.execute(sqlSession, args);
    }
  }

  /**
   * 通过反射执行 Mapper 中的默认方法，或者是 Object 的方法
   */
  private static class DefaultMethodInvoker implements MapperMethodInvoker {

    private final MethodHandle methodHandle;

    public DefaultMethodInvoker(MethodHandle methodHandle) {
      super();
      this.methodHandle = methodHandle;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession)
      throws Throwable {
      return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
  }
}
