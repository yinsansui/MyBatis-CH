/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * 语句处理器
 * @author Clinton Begin
 */
public interface StatementHandler {

  /**
   * 做准备工作
   * 1. 生成对应的 Statement
   * 2. 初始化相关参数
   * @param connection 连接
   * @param transactionTimeout 事务超时时间
   * @return 已经初始化好的表达式
   */
  Statement prepare(Connection connection, Integer transactionTimeout)
      throws SQLException;

  /**
   * 参数化处理
   * @param statement 表达式
   */
  void parameterize(Statement statement)
      throws SQLException;

  /**
   * 将 sql 语句添加到表达式中
   * @param statement 表达式
   */
  void batch(Statement statement)
      throws SQLException;

  /**
   * 执行更新方法
   * @param statement 表达式
   * @return 影响行数
   */
  int update(Statement statement)
      throws SQLException;

  /**
   * 执行查询方法
   * @param statement 表达式
   * @param resultHandler 结果处理器
   * @param <E> 返回值类型
   * @return 返回结果
   */
  <E> List<E> query(Statement statement, ResultHandler resultHandler)
      throws SQLException;

  /**
   * 执行查询方法，返回一个 Cursor
   * @param statement 表达式
   * @param <E> 返回值类型
   * @return Cursor
   */
  <E> Cursor<E> queryCursor(Statement statement)
      throws SQLException;

  /**
   * 得到 boundSql
   * @return boundSql
   */
  BoundSql getBoundSql();

  /**
   * 得到参数处理器
   * @return 参数处理器
   */
  ParameterHandler getParameterHandler();

}
