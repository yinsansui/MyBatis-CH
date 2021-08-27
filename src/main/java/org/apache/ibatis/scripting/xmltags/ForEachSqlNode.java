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
package org.apache.ibatis.scripting.xmltags;

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  // 表达式执行器
  private final ExpressionEvaluator evaluator;
  // 集合表达式，存储 <foreach> 标签 collection 属性的值
  private final String collectionExpression;
  // 当前节点
  private final SqlNode contents;
  // 要添加的前缀
  private final String open;
  // 要添加的后缀
  private final String close;
  // 元素之间的分隔符
  private final String separator;
  // 集合成员
  private final String item;
  // 代表序号的名称
  private final String index;
  // 全局配置信息
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    Map<String, Object> bindings = context.getBindings();
    // 得到需要遍历的集合
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    // 如果集合为空，则不做处理
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    // 拼接前缀 '('
    applyOpen(context);
    int i = 0;
    for (Object o : iterable) {
      DynamicContext oldContext = context;
      // 如果是一个或者没有分隔符则不需要添加前缀
      if (first || separator == null) {
        context = new PrefixedContext(context, "");
      } else {
        context = new PrefixedContext(context, separator);
      }
      // 得到唯一数字
      int uniqueNumber = context.getUniqueNumber();
      // 如果类型是 Map.Entry
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      // 拼接元素 #{__frch_id_uniqueNumber}
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      // 拼接分隔符 ','
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    // 拼接后缀 ')'
    applyClose(context);
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  // 添加变量
  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      /*
        其实 #2 代码不难理解，稍微难以理解的可能是 #1 代码
        因为在这个地方添加了值，在 foreach 节点处理完毕后又会把它删除
        要知道如果是 "#{id}" 的话会被直接替换为 "#{__frch_id_index}"
        但是如果是 ${id} 的话，就直接替换为具体的值，所以在这里添加的值其实是为了后面如果有声明了 ${id} 而准备的
       */
      context.bind(index, o); // #1
      // itemizeItem() 是为了生成全局唯一的名称，防止重复
      context.bind(itemizeItem(index, i), o); // #2
    }
  }

  // 添加变量
  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  private static class FilteredDynamicContext extends DynamicContext {
    // 被代理对象
    private final DynamicContext delegate;
    // 唯一的ID
    private final int index;
    // 元素下标的名称
    private final String itemIndex;
    // 元素名称
    private final String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      /*
        假设写的是
        <foreach collection="ids" open="(" close=")" separator="," item="id">
          #{id}
        </foreach>
        下面的代码的作用就是将 #{id} 替换为 #{__frch_id_index}
        注：index是一个数字，代表序号
       */
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          // 如果有声明 itemIndex 则尝试替换 itemIndex
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });

      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  private class PrefixedContext extends DynamicContext {
    // 被代理的 DynamicContext
    private final DynamicContext delegate;
    // 要拼接的前缀
    private final String prefix;
    // 是否拼接了前缀的标识符，之所以设置这个标识符是因为只能添加一次前缀
    // 但是 appendSql 方法可能会被调用多次
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public void appendSql(String sql) {
      // 由于 appendSql 可能会被调用多次
      // 只有第一次调用 appendSql 才需要拼接前缀
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
