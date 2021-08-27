/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.mapping;

/**
 * @author Clinton Begin
 */
public enum ParameterMode {
  /**
   * 存储过程的三种参数类型
   * IN: 输入参数
   * OUT: 输出参数
   * INOUT: 输入输出参数
   * 一个存储过程可以有多个 IN 参数，至多有一个 OUT 或 INOUT 参数
   */
  IN, OUT, INOUT
}
