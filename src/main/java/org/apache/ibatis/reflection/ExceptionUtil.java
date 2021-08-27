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
package org.apache.ibatis.reflection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * 异常工具类，用于拆开包装后的异常
 *
 * @author Clinton Begin
 */
public class ExceptionUtil {

  private ExceptionUtil() {
    // 因为是 ExceptionUtil 只有静态方法，所以禁止实例化对象
  }

  /**
   * 拆开包装后的 Throwable 对象。
   * 哪些异常需要拆开？{@link InvocationTargetException} 和 {@link UndeclaredThrowableException}
   * InvocationTargetException 的作用。InvocationTargetException 主要是用于反射的时候，
   * 因为反射调用时，一个方法的返回的异常类型可能有很多，如果直接抛出 Throwable 的话，那么范围又有点大了，所以就需要用
   * InvocationTargetException 将异常包装起来，都 throws InvocationTargetException。所以如果抛出了这个异常，
   * 就需要拆包得到具体抛出的什么异常。
   *
   * UndeclaredThrowableException 的作用。将一个受检查异常变为免检异常。主要用于代理的时候，如果代理方法没有声明
   * 受检的异常，那么就不能抛出这个受检异常，但是被代理方法可能就会抛出受检异常，所以就需要将受检异常封装为一个免检异常。
   * 使异常易于管理。
   *
   * @param wrapped 包装后的 Throwable 对象
   * @return 原始的异常信息
   */
  public static Throwable unwrapThrowable(Throwable wrapped) {
    Throwable unwrapped = wrapped;
    while (true) {
      if (unwrapped instanceof InvocationTargetException) {
        unwrapped = ((InvocationTargetException) unwrapped).getTargetException();
      } else if (unwrapped instanceof UndeclaredThrowableException) {
        unwrapped = ((UndeclaredThrowableException) unwrapped).getUndeclaredThrowable();
      } else {
        return unwrapped;
      }
    }
  }

}
