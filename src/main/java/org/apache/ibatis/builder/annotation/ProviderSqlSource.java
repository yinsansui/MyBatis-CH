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
package org.apache.ibatis.builder.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class ProviderSqlSource implements SqlSource {

  // 全局配置
  private final Configuration configuration;
  // languageDriver 用于解析 SQL
  private final LanguageDriver languageDriver;
  // 映射方法
  private final Method mapperMethod;
  // 提供 SQL 的方法所属的类
  private final Class<?> providerType;
  // 提供 SQL 的方法
  private final Method providerMethod;
  // 提供 SQL 的方法的请求参数名称
  private final String[] providerMethodArgumentNames;
  // 提供 SQL 的方法的请求参数类型
  private final Class<?>[] providerMethodParameterTypes;

  // 在提供 SQL 的方法的请求参数中只要设置了 ProviderContext，那么就会将 ProviderContext 传递进去
  // 提供当前环境的相关信息
  private final ProviderContext providerContext;
  // 提供 SQL 的方法的请求参数类型中如果有 ProviderContext 类型，这代表它的下标
  private final Integer providerContextIndex;

  /**
   * This constructor will remove at a future version.
   *
   * @param configuration
   *          the configuration
   * @param provider
   *          the provider
   * @deprecated Since 3.5.3, Please use the {@link #ProviderSqlSource(Configuration, Annotation, Class, Method)}
   *             instead of this.
   */
  @Deprecated
  public ProviderSqlSource(Configuration configuration, Object provider) {
    this(configuration, provider, null, null);
  }

  /**
   * This constructor will remove at a future version.
   *
   * @param configuration
   *          the configuration
   * @param provider
   *          the provider
   * @param mapperType
   *          the mapper type
   * @param mapperMethod
   *          the mapper method
   * @since 3.4.5
   * @deprecated Since 3.5.3, Please use the {@link #ProviderSqlSource(Configuration, Annotation, Class, Method)} instead of this.
   */
  @Deprecated
  public ProviderSqlSource(Configuration configuration, Object provider, Class<?> mapperType, Method mapperMethod) {
    this(configuration, (Annotation) provider, mapperType, mapperMethod);
  }

  /**
   * Instantiates a new provider sql source.
   *
   * @param configuration
   *          the configuration
   * @param provider
   *          the provider
   * @param mapperType
   *          the mapper type
   * @param mapperMethod
   *          the mapper method
   * @since 3.5.3
   */
  public ProviderSqlSource(Configuration configuration, Annotation provider, Class<?> mapperType, Method mapperMethod) {
    String candidateProviderMethodName;
    Method candidateProviderMethod = null;
    try {
    this.configuration = configuration;
    this.mapperMethod = mapperMethod;
    Lang lang = mapperMethod == null ? null : mapperMethod.getAnnotation(Lang.class);
    this.languageDriver = configuration.getLanguageDriver(lang == null ? null : lang.value());
    // 得到提供 SQL 的类
    this.providerType = getProviderType(configuration, provider, mapperMethod);
    // 得到提供 SQL 的方法名称
    candidateProviderMethodName = (String) provider.annotationType().getMethod("method").invoke(provider);

    // 如果没有声明提供 SQL 的方法名称，但是提供 SQL 的类实现了 ProviderMethodResolver，那么就调用其解析方法
    if (candidateProviderMethodName.length() == 0 && ProviderMethodResolver.class.isAssignableFrom(this.providerType)) {
      candidateProviderMethod = ((ProviderMethodResolver) this.providerType.getDeclaredConstructor().newInstance())
          .resolveMethod(new ProviderContext(mapperType, mapperMethod, configuration.getDatabaseId()));
    }
    if (candidateProviderMethod == null) {
      // 如果没有声明提供 SQL 的方法名称，则默认名称为 "provideSql"
      // 通过名称在类中找到对应的方法
      candidateProviderMethodName = candidateProviderMethodName.length() == 0 ? "provideSql" : candidateProviderMethodName;
      for (Method m : this.providerType.getMethods()) {
        if (candidateProviderMethodName.equals(m.getName()) && CharSequence.class.isAssignableFrom(m.getReturnType())) {
          if (candidateProviderMethod != null) {
            throw new BuilderException("Error creating SqlSource for SqlProvider. Method '"
                + candidateProviderMethodName + "' is found multiple in SqlProvider '" + this.providerType.getName()
                + "'. Sql provider method can not overload.");
          }
          candidateProviderMethod = m;
        }
      }
    }
    } catch (BuilderException e) {
      throw e;
    } catch (Exception e) {
      throw new BuilderException("Error creating SqlSource for SqlProvider.  Cause: " + e, e);
    }
    // 如果还是没有找到提供 SQL 的方法，则直接抛出异常
    if (candidateProviderMethod == null) {
      throw new BuilderException("Error creating SqlSource for SqlProvider. Method '"
          + candidateProviderMethodName + "' not found in SqlProvider '" + this.providerType.getName() + "'.");
    }
    this.providerMethod = candidateProviderMethod;
    this.providerMethodArgumentNames = new ParamNameResolver(configuration, this.providerMethod).getNames();
    this.providerMethodParameterTypes = this.providerMethod.getParameterTypes();

    // 如果提供 SQL 的方法中声明了 ProviderContext 则需要得到它的下标和要传入的信息
    // 可以在调用的时候将参数传递进去
    ProviderContext candidateProviderContext = null;
    Integer candidateProviderContextIndex = null;
    for (int i = 0; i < this.providerMethodParameterTypes.length; i++) {
      Class<?> parameterType = this.providerMethodParameterTypes[i];
      if (parameterType == ProviderContext.class) {
        if (candidateProviderContext != null) {
          throw new BuilderException("Error creating SqlSource for SqlProvider. ProviderContext found multiple in SqlProvider method ("
              + this.providerType.getName() + "." + providerMethod.getName()
              + "). ProviderContext can not define multiple in SqlProvider method argument.");
        }
        candidateProviderContext = new ProviderContext(mapperType, mapperMethod, configuration.getDatabaseId());
        candidateProviderContextIndex = i;
      }
    }
    this.providerContext = candidateProviderContext;
    this.providerContextIndex = candidateProviderContextIndex;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    SqlSource sqlSource = createSqlSource(parameterObject);
    return sqlSource.getBoundSql(parameterObject);
  }

  private SqlSource createSqlSource(Object parameterObject) {
    try {
      /* 1. 得到 SQL 语句 */
      String sql;
      // 如果请求参数为 Map 类型
      if (parameterObject instanceof Map) {
        int bindParameterCount = providerMethodParameterTypes.length - (providerContext == null ? 0 : 1);
        // 排除 ProviderContext 只有一个参数，并且参数的类型也是 Map 类型，则封装到 Object[] 中然后调用方法
        if (bindParameterCount == 1
            && providerMethodParameterTypes[Integer.valueOf(0).equals(providerContextIndex) ? 1 : 0].isAssignableFrom(parameterObject.getClass())) {
          sql = invokeProviderMethod(extractProviderMethodArguments(parameterObject));
        }
        else {
          // 尝试从 Map 的内容中构建出 ProviderMethod 的请求参数
          @SuppressWarnings("unchecked")
          Map<String, Object> params = (Map<String, Object>) parameterObject;
          sql = invokeProviderMethod(extractProviderMethodArguments(params, providerMethodArgumentNames));
        }
      }
      // 没有声明入参，直接无参调用
      else if (providerMethodParameterTypes.length == 0) {
        sql = invokeProviderMethod();
      }
      // 只有一个入参的
      else if (providerMethodParameterTypes.length == 1) {
        // 如果不是传递 ProviderContext，则传递请求参数过去
        if (providerContext == null) {
          sql = invokeProviderMethod(parameterObject);
        }
        // 如果是声明的 ProviderContext，则传递 ProviderContext
        else {
          sql = invokeProviderMethod(providerContext);
        }
      }
      // 如果声明了两个入参
      else if (providerMethodParameterTypes.length == 2) {
        sql = invokeProviderMethod(extractProviderMethodArguments(parameterObject));
      } else {
        throw new BuilderException("Cannot invoke SqlProvider method '" + providerMethod
          + "' with specify parameter '" + (parameterObject == null ? null : parameterObject.getClass())
          + "' because SqlProvider method arguments for '" + mapperMethod + "' is an invalid combination.");
      }
      Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();

      /* 2. 调用 LanguageDriver 得到 SqlSource */
      return languageDriver.createSqlSource(configuration, sql, parameterType);
    } catch (BuilderException e) {
      throw e;
    } catch (Exception e) {
      throw new BuilderException("Error invoking SqlProvider method '" + providerMethod
          + "' with specify parameter '" + (parameterObject == null ? null : parameterObject.getClass()) + "'.  Cause: " + extractRootCause(e), e);
    }
  }

  private Throwable extractRootCause(Exception e) {
    Throwable cause = e;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private Object[] extractProviderMethodArguments(Object parameterObject) {
    if (providerContext != null) {
      Object[] args = new Object[2];
      args[providerContextIndex == 0 ? 1 : 0] = parameterObject;
      args[providerContextIndex] = providerContext;
      return args;
    } else {
      return new Object[] { parameterObject };
    }
  }

  // 封装为 ProviderMethod 的请求参数
  private Object[] extractProviderMethodArguments(Map<String, Object> params, String[] argumentNames) {
    Object[] args = new Object[argumentNames.length];
    for (int i = 0; i < args.length; i++) {
      if (providerContextIndex != null && providerContextIndex == i) {
        args[i] = providerContext;
      } else {
        args[i] = params.get(argumentNames[i]);
      }
    }
    return args;
  }

  private String invokeProviderMethod(Object... args) throws Exception {
    Object targetObject = null;
    // 如果不是静态方法的话，则需要先生成目标对象
    if (!Modifier.isStatic(providerMethod.getModifiers())) {
      targetObject = providerType.getDeclaredConstructor().newInstance();
    }
    CharSequence sql = (CharSequence) providerMethod.invoke(targetObject, args);
    return sql != null ? sql.toString() : null;
  }

  private Class<?> getProviderType(Configuration configuration, Annotation providerAnnotation, Method mapperMethod)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    // 通过反射得到 xxxProvider 中 type 和 value 的值
    // 因为注解没有继承这一说法，所以可能有些不太明白
    Class<?> type = (Class<?>) providerAnnotation.annotationType().getMethod("type").invoke(providerAnnotation);
    Class<?> value = (Class<?>) providerAnnotation.annotationType().getMethod("value").invoke(providerAnnotation);
    if (value == void.class && type == void.class) {
      if (configuration.getDefaultSqlProviderType() != null) {
        return configuration.getDefaultSqlProviderType();
      }
      throw new BuilderException("Please specify either 'value' or 'type' attribute of @"
          + providerAnnotation.annotationType().getSimpleName()
          + " at the '" + mapperMethod.toString() + "'.");
    }
    if (value != void.class && type != void.class && value != type) {
      throw new BuilderException("Cannot specify different class on 'value' and 'type' attribute of @"
          + providerAnnotation.annotationType().getSimpleName()
          + " at the '" + mapperMethod.toString() + "'.");
    }
    return value == void.class ? type : value;
  }

}
