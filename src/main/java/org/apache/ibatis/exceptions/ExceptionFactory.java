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
package org.apache.ibatis.exceptions;

import org.apache.ibatis.executor.ErrorContext;

/**
 * @author Clinton Begin
 */
public class ExceptionFactory {

  private ExceptionFactory() {
    // 由于 ExceptionFactory 只有静态方法，所以不需要实例化
  }

  public static RuntimeException wrapException(String message, Exception e) {
    // 构建 PersistenceException 对象
    return new PersistenceException(ErrorContext.instance().message(message).cause(e).toString(), e);
  }

}
