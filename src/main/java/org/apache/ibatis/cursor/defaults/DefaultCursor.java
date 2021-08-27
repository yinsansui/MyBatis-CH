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
package org.apache.ibatis.cursor.defaults;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * This is the default implementation of a MyBatis Cursor.
 * This implementation is not thread safe.
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public class DefaultCursor<T> implements Cursor<T> {

  // 默认的结果集处理器
  private final DefaultResultSetHandler resultSetHandler;
  // 结果集映射，信息源自 Mapper 映射文件中 <ResultMap> 节点
  private final ResultMap resultMap;
  // ResultSet 包装类
  private final ResultSetWrapper rsw;
  // 结果的起止信息
  private final RowBounds rowBounds;
  // ResultHandler 的子类，起到暂存结果的作用
  protected final ObjectWrapperResultHandler<T> objectWrapperResultHandler = new ObjectWrapperResultHandler<>();

  // 游标的内部迭代器
  private final CursorIterator cursorIterator = new CursorIterator();
  // 迭代器获取标志位，限制迭代器只能被获取一次
  private boolean iteratorRetrieved;

  // 当前游标状态
  private CursorStatus status = CursorStatus.CREATED;
  // 记录已经映射的行
  private int indexWithRowBound = -1;

  private enum CursorStatus {

    // 表示新创建的游标，结果集尚未消费
    CREATED,
    // 表示游标正在被使用，结果集正在被消费
    OPEN,
    // 表示游标已经被关闭，但其中的结果集未被完全消费
    CLOSED,
    // 表示游标已经被关闭，其中的结果集已经被完全消费
    CONSUMED
  }

  public DefaultCursor(DefaultResultSetHandler resultSetHandler, ResultMap resultMap, ResultSetWrapper rsw, RowBounds rowBounds) {
    this.resultSetHandler = resultSetHandler;
    this.resultMap = resultMap;
    this.rsw = rsw;
    this.rowBounds = rowBounds;
  }

  @Override
  public boolean isOpen() {
    return status == CursorStatus.OPEN;
  }

  @Override
  public boolean isConsumed() {
    return status == CursorStatus.CONSUMED;
  }

  @Override
  public int getCurrentIndex() {
    return rowBounds.getOffset() + cursorIterator.iteratorIndex;
  }

  @Override
  public Iterator<T> iterator() {
    if (iteratorRetrieved) {
      throw new IllegalStateException("Cannot open more than one iterator on a Cursor");
    }
    if (isClosed()) {
      throw new IllegalStateException("A Cursor is already closed.");
    }
    iteratorRetrieved = true;
    return cursorIterator;
  }

  @Override
  public void close() {
    // 如果已经关闭了则不管
    if (isClosed()) {
      return;
    }

    ResultSet rs = rsw.getResultSet();
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    } finally {
      status = CursorStatus.CLOSED;
    }
  }

  // 在考虑起始边界的情况下获取下一个值
  protected T fetchNextUsingRowBound() {
    T result = fetchNextObjectFromDatabase();
    // 在没有到达起始边界前，就一直获取下一个值
    while (objectWrapperResultHandler.fetched && indexWithRowBound < rowBounds.getOffset()) {
      result = fetchNextObjectFromDatabase();
    }
    return result;
  }

  // 从数据库获取下一个对象
  protected T fetchNextObjectFromDatabase() {
    // 游标被关闭了则直接返回 null
    if (isClosed()) {
      return null;
    }

    try {
      // 重置结果为没有被获取
      objectWrapperResultHandler.fetched = false;
      // 设置游标状态为 OPEN
      status = CursorStatus.OPEN;
      if (!rsw.getResultSet().isClosed()) {// 如果结果集还没有关闭
        // 从结果集中取出一条记录，将其转换为对象，并存入 objectWrapperResultHandler 中
        resultSetHandler.handleRowValues(rsw, resultMap, objectWrapperResultHandler, RowBounds.DEFAULT, null);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    // 获取存入 objectWrapperResultHandler 中的对象
    T next = objectWrapperResultHandler.result;
    if (objectWrapperResultHandler.fetched) {// 如果得到了新对象
      // 索引 + 1
      indexWithRowBound++;
    }

    if (!objectWrapperResultHandler.fetched || getReadItemsCount() == rowBounds.getOffset() + rowBounds.getLimit()) {
      // 如果没有获取到新的对象或者已经到达了读取边界说明已经读完了
      // 关闭连接，设置游标状态为已结束
      close();
      status = CursorStatus.CONSUMED;
    }
    // objectWrapperResultHandler 中暂存的值置空
    objectWrapperResultHandler.result = null;

    return next;
  }

  private boolean isClosed() {
    return status == CursorStatus.CLOSED || status == CursorStatus.CONSUMED;
  }

  private int getReadItemsCount() {
    return indexWithRowBound + 1;
  }

  protected static class ObjectWrapperResultHandler<T> implements ResultHandler<T> {
    // 结果
    protected T result;
    // 是否可获取
    protected boolean fetched;

    @Override
    public void handleResult(ResultContext<? extends T> context) {
      // 取出上下文中的一条结果
      this.result = context.getResultObject();
      // 关闭上下文
      context.stop();
      // 设置有结果可获取
      fetched = true;
    }
  }

  protected class CursorIterator implements Iterator<T> {

    // 下一个待返回的对象，在 hasNext 方法中完成写入
    T object;

    // 迭代器的下标
    int iteratorIndex = -1;

    @Override
    public boolean hasNext() {
      if (!objectWrapperResultHandler.fetched) {
        // 如果没有结果可以获取，则先去获取
        object = fetchNextUsingRowBound();
      }
      return objectWrapperResultHandler.fetched;
    }

    @Override
    public T next() {
      T next = object;

      if (!objectWrapperResultHandler.fetched) {
        // 如果没有结果可以获取，则先去获取
        next = fetchNextUsingRowBound();
      }

      if (objectWrapperResultHandler.fetched) {
        // 如果有结果可以获取，说明当前的值还没有被获取过
        // 设置值已经被获取了，然后返回出去
        objectWrapperResultHandler.fetched = false;
        // 清空暂存的值
        object = null;
        // 迭代器下标 + 1
        iteratorIndex++;
        return next;
      }
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Cannot remove element from Cursor");
    }
  }
}
