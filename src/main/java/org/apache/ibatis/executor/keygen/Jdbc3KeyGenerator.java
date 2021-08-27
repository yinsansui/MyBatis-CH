/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession.StrictMap;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * Jdbc3KeyGenerator 是为具有主键自增功能的数据库准备的，因为主键是由数据库生成的，所以需要将数据库生成的主键回写到指定的字段中
 * 要不然虽然数据库生成了，但是在代码里面去获取ID还是为 null
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  private static final String SECOND_GENERIC_PARAM_NAME = ParamNameResolver.GENERIC_NAME_PREFIX + "2";

  /**
   * A shared instance.
   *
   * @since 3.4.3
   */
  public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

  private static final String MSG_TOO_MANY_KEYS = "Too many keys are generated. There are only %d target objects. "
      + "You either specified a wrong 'keyProperty' or encountered a driver bug like #1523.";

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // 由于自增的主键是在数据库插入结束后生成，所以再插入前不需要做任何处理
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    processBatch(ms, stmt, parameter);
  }

  public void processBatch(MappedStatement ms, Statement stmt, Object parameter) {
    // 得到 key 属性名称列表
    final String[] keyProperties = ms.getKeyProperties();
    if (keyProperties == null || keyProperties.length == 0) {
      return;
    }
    try (ResultSet rs = stmt.getGeneratedKeys()) {
    // 获取输出结果的描述信息
    final ResultSetMetaData rsmd = rs.getMetaData();
    final Configuration configuration = ms.getConfiguration();
    if (rsmd.getColumnCount() < keyProperties.length) {
      // 查询得到的结果列数比待回填属性要少的时候不做任何处理
      // 即，要么所有属性都回填，要么都不回填
    } else {
      // 进行值的回填
      assignKeys(configuration, rs, rsmd, keyProperties, parameter);
    }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    }
  }

  @SuppressWarnings("unchecked")
  private void assignKeys(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd, String[] keyProperties,
      Object parameter) throws SQLException {
    /*
      这里的判断逻辑，有点涉及到前面的知识，但是在这里不会再回顾前面的知识。
      如果忘记了，大家可以先去看看之前的文章，涉及的知识点是 reflection 包(二) 中的 ParamNameResolver 类的。
      这里根据不同的情况调用了三个方法，但是我只会讲解方法1和方法3，因为方法2内容实现是依托于方法1的，
      只要弄懂了方法1，相信方法2你们也是能看懂的。
     */
    if (parameter instanceof ParamMap || parameter instanceof StrictMap) {
      // 方法1：有多个入参，或者只有一个入参但是声明了 @Param 注解
      assignKeysToParamMap(configuration, rs, rsmd, keyProperties, (Map<String, ?>) parameter);
    } else if (parameter instanceof ArrayList && !((ArrayList<?>) parameter).isEmpty()
        && ((ArrayList<?>) parameter).get(0) instanceof ParamMap) {
      // 方法2：有多个入参，或者只有一个带 @Param 注解的入参，在批量操作的情况下
      assignKeysToParamMapList(configuration, rs, rsmd, keyProperties, (ArrayList<ParamMap<?>>) parameter);
    } else {
      // 方法3：只有一个入参，并且没有声明 @Param 注解的情况
      assignKeysToParam(configuration, rs, rsmd, keyProperties, parameter);
    }
  }

  // 方法3：将键值分配给参数
  private void assignKeysToParam(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, Object parameter) throws SQLException {
    Collection<?> params = collectionize(parameter);
    if (params.isEmpty()) {
      return;
    }
    // 1. 先为每个待回填属性创建一个回填器
    List<KeyAssigner> assignerList = new ArrayList<>();
    for (int i = 0; i < keyProperties.length; i++) {
      assignerList.add(new KeyAssigner(configuration, rsmd, i + 1, null, keyProperties[i]));
    }

    // 2. 遍历结果集，利用回填器回填属性值
    Iterator<?> iterator = params.iterator();
    while (rs.next()) {
      if (!iterator.hasNext()) {// 如果 ResultSet 中的记录数比入参对象数量还要多
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, params.size()));
      }
      // 为得到入参对象，并为其中的 key 回填值
      Object param = iterator.next();
      assignerList.forEach(x -> x.assign(rs, param));
    }
  }

  private void assignKeysToParamMapList(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, ArrayList<ParamMap<?>> paramMapList) throws SQLException {
    Iterator<ParamMap<?>> iterator = paramMapList.iterator();
    List<KeyAssigner> assignerList = new ArrayList<>();
    long counter = 0;
    while (rs.next()) {
      if (!iterator.hasNext()) {
        // 数据库查询返回的记录数量比要插入列表的数量多的时候抛出异常
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
      }
      ParamMap<?> paramMap = iterator.next();
      if (assignerList.isEmpty()) {
        // 如果是第一个进入，则需要创建所有回填器
        for (int i = 0; i < keyProperties.length; i++) {
          assignerList
              .add(getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i], keyProperties, false)
                  .getValue());
        }
      }
      // 进行属性的回填
      assignerList.forEach(x -> x.assign(rs, paramMap));
      counter++;
    }
  }

  // 方法1：将键值回填到 ParamMap 中的对象
  private void assignKeysToParamMap(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
      String[] keyProperties, Map<String, ?> paramMap) throws SQLException {
    // 如果没有入参数不需要回填
    if (paramMap.isEmpty()) {
      return;
    }

    // 1. 得到属性的回填器
    // key->需要回填的属性名称 value-> (key -> 待回填的入参对象，被封装为集合 value -> 某一个属性的多个回填器)
    Map<String, Entry<Iterator<?>, List<KeyAssigner>>> assignerMap = new HashMap<>();
    for (int i = 0; i < keyProperties.length; i++) {
      // 先得到回填器
      Entry<String, KeyAssigner> entry = getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i],
          keyProperties, true);
      // 自增主键可能需要放到一个列表中
      // key -> 待回填的入参对象，被封装为集合 value -> 某一个属性的多个回填器
      Entry<Iterator<?>, List<KeyAssigner>> iteratorPair = assignerMap.computeIfAbsent(entry.getKey(),
          k -> entry(collectionize(paramMap.get(k)).iterator(), new ArrayList<>()));
      // 将回填器放到列表中
      iteratorPair.getValue().add(entry.getValue());
    }

    // 2. 遍历结果集，利用回填器，为入参回填属性值
    long counter = 0;
    while (rs.next()) {
      for (Entry<Iterator<?>, List<KeyAssigner>> pair : assignerMap.values()) {
        if (!pair.getKey().hasNext()) {// 集合中元素的数量少于 RestSet 中查询结果的数量，抛出异常
          throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
        }
        // 由于是迭代器，所以每次获取的都是紧接上次的下一个元素
        // 得到待回填的对象，并通过回填器对属性进行回填
        Object param = pair.getKey().next();
        pair.getValue().forEach(x -> x.assign(rs, param));
      }
      counter++;
    }
  }

  /**
   * 获取 Key 分配器
   * @param config 全局配置信息
   * @param rsmd 查询返回结果的描述信息
   * @param columnPosition 列位置，从 1 开始
   * @param paramMap 请求参数
   * @param keyProperty 当前属性名称
   * @param keyProperties 所有属性名称
   * @param omitParamName 是否忽略参数名称
   * @return Key 分配器
   */
  private Entry<String, KeyAssigner> getAssignerForParamMap(Configuration config, ResultSetMetaData rsmd,
      int columnPosition, Map<String, ?> paramMap, String keyProperty, String[] keyProperties, boolean omitParamName) {
    Set<String> keySet = paramMap.keySet();
    // 如果 paramMap 中没有 "param2" 这个名称的 key，说明实际上只有一个请求参数
    // 注意：在这里其实 MyBatis 中的源码有提到，你只有一个入参，但是你声明的是 @Param("param2") 的话，也可能会造成误判
    // 认为有多个入参。所以为了避免回填失败，你必须要在 keyProperty 中填写 param2.x，而不是直接写 x
    boolean singleParam = !keySet.contains(SECOND_GENERIC_PARAM_NAME);
    int firstDot = keyProperty.indexOf('.');
    if (firstDot == -1) {
      if (singleParam) {
        // 如果没有 '.' 并且只有一个请求参数，交给指定的方法得到回填器
        return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
      }
      // 如果没有 '.' 但是有多个入参，所以不知道回填到哪个入参中
      throw new ExecutorException("Could not determine which parameter to assign generated keys to. "
          + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
          + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
          + keySet);
    }

    String paramName = keyProperty.substring(0, firstDot);
    if (keySet.contains(paramName)) {// 如果有指定的请求参数名称
      // 得到 ParamMap 中对应 key 的名称
      String argParamName = omitParamName ? null : paramName;
      // 得到入参对象中要回填属性的名称
      String argKeyProperty = keyProperty.substring(firstDot + 1);
      // 构建回填器并返回
      return entry(paramName, new KeyAssigner(config, rsmd, columnPosition, argParamName, argKeyProperty));
    } else if (singleParam) {// 如果只有一个请求参数，但是还是写了前缀，例如 param2.id，还是交给指定方法得到回填器
      return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
    } else {
      throw new ExecutorException("Could not find parameter '" + paramName + "'. "
          + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
          + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
          + keySet);
    }
  }

  /**
   * 从唯一的请求参数中获取分配器
   * @param config MyBatis全局配置信息
   * @param rsmd 数据库查询结果的描述信息
   * @param columnPosition 列的位置
   * @param paramMap 请求参数Map
   * @param keyProperty 属性
   * @param omitParamName 是否忽略参数名称
   * @return key -> 属性名称, value -> 属性值回填器
   */
  private Entry<String, KeyAssigner> getAssignerForSingleParam(Configuration config, ResultSetMetaData rsmd,
      int columnPosition, Map<String, ?> paramMap, String keyProperty, boolean omitParamName) {
    // 从 paramMap 中获取参数名称，因为这个方法处理的就是只有一个请求参数的情况，所以取第一个值就是我们想找的参数名称
    String singleParamName = nameOfSingleParam(paramMap);
    // 得到请求参数名称
    String argParamName = omitParamName ? null : singleParamName;
    // 构建分配器
    return entry(singleParamName, new KeyAssigner(config, rsmd, columnPosition, argParamName, keyProperty));
  }

  private static String nameOfSingleParam(Map<String, ?> paramMap) {
    // There is virtually one parameter, so any key works.
    return paramMap.keySet().iterator().next();
  }

  /**
   * 将参数转换为集合类型并返回
   * @param param 请求参数
   * @return 集合类型
   */
  private static Collection<?> collectionize(Object param) {
    if (param instanceof Collection) {
      return (Collection<?>) param;
    } else if (param instanceof Object[]) {
      return Arrays.asList((Object[]) param);
    } else {
      return Arrays.asList(param);
    }
  }

  private static <K, V> Entry<K, V> entry(K key, V value) {
    // Replace this with Map.entry(key, value) in Java 9.
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }

  /**
   * Key 分配器
   */
  private class KeyAssigner {
    // MyBatis 的全局配置
    private final Configuration configuration;
    // 数据库返回结果的描述信息
    private final ResultSetMetaData rsmd;
    // 类型处理器的注册表
    private final TypeHandlerRegistry typeHandlerRegistry;
    // 列的位置
    private final int columnPosition;
    // ParamMap 中的参数名称
    private final String paramName;
    // 参数对象中属性名称
    private final String propertyName;
    // 类型处理器
    private TypeHandler<?> typeHandler;

    protected KeyAssigner(Configuration configuration, ResultSetMetaData rsmd, int columnPosition, String paramName,
        String propertyName) {
      super();
      this.configuration = configuration;
      this.rsmd = rsmd;
      this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      this.columnPosition = columnPosition;
      this.paramName = paramName;
      this.propertyName = propertyName;
    }

    protected void assign(ResultSet rs, Object param) {
      if (paramName != null) {
        // 如果设置了 paramName 说明请求参数是 ParamMap 类型
        // 得到实际需要回填的入参
        param = ((ParamMap<?>) param).get(paramName);
      }
      MetaObject metaParam = configuration.newMetaObject(param);
      try {
      // 得到类型处理器
      if (typeHandler == null) {
        if (metaParam.hasSetter(propertyName)) {
          Class<?> propertyType = metaParam.getSetterType(propertyName);
          typeHandler = typeHandlerRegistry.getTypeHandler(propertyType,
              JdbcType.forCode(rsmd.getColumnType(columnPosition)));
        } else {
          throw new ExecutorException("No setter found for the keyProperty '" + propertyName + "' in '"
              + metaParam.getOriginalObject().getClass().getName() + "'.");
        }
      }

      if (typeHandler == null) {
        // 类型处理器不存在，则不进行回填
      } else {
        // 回填值
        Object value = typeHandler.getResult(rs, columnPosition);
        metaParam.setValue(propertyName, value);
      }
      } catch (SQLException e) {
        throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e,
            e);
      }
    }
  }
}
