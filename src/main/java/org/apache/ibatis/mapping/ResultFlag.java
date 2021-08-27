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
public enum ResultFlag {
  /*
  <resultMap id="testResultMap" type="User">
    <constructor>
      <idArg column="id" name="id"/>  -- 是ID，也是构造器参数
      <arg column="name" name="name"/> -- 是构造器参数，但不是ID
    </constructor>
    <id column="pId" property="pId" /> -- 是ID，但不是构造器参数
    <result column="pName" property="pName"/> -- 既不是ID，也不是构造器参数
  </resultMap>
   */
  ID, CONSTRUCTOR
}
