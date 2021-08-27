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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  /**
   * sql
   */
  private final SqlCommand command;

  /**
   * 方法签名
   */
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    // SQL 命令
    this.command = new SqlCommand(config, mapperInterface, method);
    // 方法签名
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      // 插入语句
      case INSERT: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      // 修改语句
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      // 删除语句
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      // 查询语句
      case SELECT:
        if (method.returnsVoid() && method.hasResultHandler()) {
          // 如果没有返回值，并且请求参数中有 ResultHandler 才执行这个方法
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          // 如果返回参数是一个集合或者数组的话，则调用这个方法
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          // 如果返回的是一个 Map 类型
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          // 如果返回的是一个游标
          result = executeForCursor(sqlSession, args);
        } else {
          // 如果只是普通的返回类型
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional()
            && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
        // 只是用来刷新缓存的语句
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    // 如果要求返回一个原生类型，但是 result 又是 null，那么就需要抛出异常
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
        + " attempted to return null from a method with a primitive return type (" + method
        .getReturnType() + ").");
    }
    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE
      .equals(method.getReturnType())) {
      // 如果是 int 原型或者其包装类 Integer，则直接赋值
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE
      .equals(method.getReturnType())) {
      // 如果是 long 原型或者其包装类 Long，则强转后赋值
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE
      .equals(method.getReturnType())) {
      // 如果 boolean 原型或者其包装类 Boolean，则判断 rowCount 是不是大于 0
      result = rowCount > 0;
    } else {
      throw new BindingException(
        "Mapper method '" + command.getName() + "' has an unsupported return type: " + method
          .getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
      && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
        + " needs either a @ResultMap annotation, a @ResultType annotation,"
        + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    // 根据是否有 RowBounds 参数来选择执行什么方法
    if (method.hasRowBounds()) {

      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    // 根据是否有 RowBound 来决定是否哪个方法
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      // 如果实际的 Result 类型并不能直接赋值给方法签名中声明的返回值类型，就需要做类型的转换
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    // 根据是否有 RowBound 来决定调用哪个重载的方法
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    // getComponentType 如果 class 是一个数组类型的话，就返回它包含的元素的类型
    // 例如 Integer[] arr; 如果是 arr.getClass() 返回的是 Array，如果是 getComponentType 返回的才是 Integer
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) {
      // 如果是原生的，就只能通过 Array，一个一个赋值
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      // 如果不是原生的，则可以直接调用 list.toArray
      return list.toArray((E[]) array);
    }
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    // 根据是否有 RowBounds 来决定调用哪个重载的方法
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException(
          "Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  public static class SqlCommand {

    private final String name;
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      // 构建 MappedStatement，实际上是从 Configuration 中获取的 MappedStatement
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
        configuration);
      if (ms == null) {
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
            + mapperInterface.getName() + "." + methodName);
        }
      } else {
        // SQL 指令的名称为 MappedStatement 的ID，是唯一的
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * 解析得到 MappedStatement
     *
     * @param mapperInterface Mapper 接口
     * @param methodName      方法名称
     * @param declaringClass  方法声明的类
     * @param configuration   配置
     * @return MappedStatement
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
      Class<?> declaringClass, Configuration configuration) {
      // 拼接接口中方法的ID com.yinxy.demo.UserMapper.selectById
      String statementId = mapperInterface.getName() + "." + methodName;
      if (configuration.hasStatement(statementId)) {
        return configuration.getMappedStatement(statementId);
      } else if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
            declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  public static class MethodSignature {

    // 返回值类型为 Collection 或 Array
    private final boolean returnsMany;
    // 返回值类型为 Map
    private final boolean returnsMap;
    // 没有返回值
    private final boolean returnsVoid;
    // 返回值类型为 Cursor
    private final boolean returnsCursor;
    // 返回值类型为 Optional
    private final boolean returnsOptional;
    // 返回值类型
    private final Class<?> returnType;
    // mapKey的值
    private final String mapKey;

    // 请求参数中 ResultHandler 的下标
    private final Integer resultHandlerIndex;
    // 请求参数中 RowBounds 的下标
    private final Integer rowBoundsIndex;
    // 请求参数名称解析器
    private final ParamNameResolver paramNameResolver;

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 首先获取方法的返回值类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      // 是否返回的是 void
      this.returnsVoid = void.class.equals(this.returnType);
      // 是否返回的是集合或数组
      this.returnsMany =
        configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      // 是否返回时游标
      this.returnsCursor = Cursor.class.equals(this.returnType);
      // 是否返回的是 optional
      this.returnsOptional = Optional.class.equals(this.returnType);
      // 得到 MapKey
      this.mapKey = getMapKey(method);
      // 如果存在 MapKey 说明返回值类型是 Map
      this.returnsMap = this.mapKey != null;
      // 得到请求参数类型为 RowBounds 的下标
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      // 得到请求参数类型为 ResultHandler 的下标
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      // 得到参数名称解析器
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    /**
     * 得到方法中所有的参数名称到值的映射
     *
     * @param args 参数
     * @return 映射
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    /**
     * 是否有 RowBounds 类型的请求参数
     *
     * @return 是否有
     */
    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    /**
     * 将请求参数中的 RowBounds 提取出来
     *
     * @param args 方法的所有请求参数
     * @return RowRounds 参数
     */
    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    /**
     * 请求参数中是否包含 ResultHandler 类型的参数
     *
     * @return 是否包含
     */
    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    /**
     * 提取出 ResultHandler 请求参数
     *
     * @param args 所有的请求参数
     * @return ResultHandler
     */
    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            index = i;
          } else {
            throw new BindingException(
              method.getName() + " cannot have multiple " + paramType.getSimpleName()
                + " parameters");
          }
        }
      }
      return index;
    }

    public String getMapKey() {
      return mapKey;
    }

    /**
     * 如果方法的返回值类型是 Map，那么就尝试获取 MapKey
     *
     * @param method 方法
     * @return MapKey
     */
    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
