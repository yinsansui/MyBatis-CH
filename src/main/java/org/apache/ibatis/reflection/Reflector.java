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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that allows for easy mapping
 * between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * Class 类型
   */
  private final Class<?> type;

  /**
   * 可读的属性名称
   */
  private final String[] readablePropertyNames;

  /**
   * 可写的属性名称
   */
  private final String[] writablePropertyNames;

  /**
   * 缓存类中的 set 方法映射。key -> 属性名，value -> 方法的 Invoker
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();

  /**
   * 缓存类中的 get 方法映射。key -> 属性名，value -> 方法的 Invoker
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();

  /**
   * 缓存类中的 set 方法的请求参数类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();

  /**
   * 缓存类中 get 方法的返回类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();

  /**
   * 类的默认的构造方法
   */
  private Constructor<?> defaultConstructor;

  /**
   * 全大写的属性名称 -> 标准属性名称
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    // 设置 Reflector 的类型
    type = clazz;
    // 设置默认的构造器
    addDefaultConstructor(clazz);
    // 设置 get 方法和它的返回值类型
    addGetMethods(clazz);
    // 设置 set 方法和它的请求参数类型
    addSetMethods(clazz);
    // 对没有 set 或 get 方法的属性做补充
    // 即如果有 set 或 get 方法则调用对应的方法，如果没有的话则通过反射直接对 field 进行赋值
    addFields(clazz);
    // getMethods 中的所有 key 即属性都是可读的
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // setMethods 中所有的 key 即属性名称都是可写的
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    for (String propName : readablePropertyNames) {
      // 添加属性名映射：全大写的属性名称 -> 标准属性名称
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      // 添加属性名映射：全大写的属性名称 -> 标准属性名称
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 设置默认的类构造器
   *
   * @param clazz 类
   */
  private void addDefaultConstructor(Class<?> clazz) {
    // 获得类所有的构造器
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    // 找到无参的构造器，如果存在的话，则设置为默认的构造器
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  /**
   * 设置属性的 get 方法及其返回值类型
   *
   * @param clazz 类型
   */
  private void addGetMethods(Class<?> clazz) {
    // 存放可能存在冲突的 get 方法。key -> 属性名称，value -> 对应的get方法
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获取一个类的所有方法
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods)
      // 只留下符合要求的 getXXX 方法
      .filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      // 将方法添加到 conflictingGetters 中
      .forEach(
        m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));

    // 解析有冲突的 conflictingGetters 列表
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 解析 conflictingGetters 中有冲突的列表，即一个属性名称可能对应了多个 getXXX 方法，要从中选择最合适的 get 方法
   *
   * @param conflictingGetters 可能有冲突的 getXXX 方法映射
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 当前有冲突列表中最合适的 get 方法
      Method winner = null;
      String propName = entry.getKey();
      // 最后的结果是否是 '唯一解' 的，即可能有多个答案都一样合适
      boolean isAmbiguous = false;
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          // 确保 winner 有一个默认的值
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        if (candidateType.equals(winnerType)) {
          if (!boolean.class.equals(candidateType)) {
            // 如果 winnerType 和 candidateType 是一样的，并且不是 boolean 类型的
            // 说明没有 '唯一解'
            isAmbiguous = true;
            break;
          } else if (candidate.getName().startsWith("is")) {
            // 如果类型是 boolean，并且 candidate 方法的名字是 isXXX
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // 如果 candidateType 等于 winnerType 或者是 winnerType 的父类，那么就忽略
          // 因为返回值类型粒度当然越小越好
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 如果 winnerType 等于 candidateType 或者是 candidateType 的父类
          winner = candidate;
        } else {
          // 如果无法进行判断，那么就认为不存在 '唯一解'
          isAmbiguous = true;
          break;
        }
      }
      // 将 winner 放入到 getMethods 属性中
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  /**
   * 添加 get 方法到 getMethods 和 getTypes 中
   *
   * @param name        getXXX 方法名称解析后得到的名称
   * @param method      getXXX 方法
   * @param isAmbiguous 是否是 '唯一解'
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 如果是 '唯一解' 那么就直接生成 MethodInvoker，如果有 '多个解'，则封装为 AmbiguousMethodInvoker，在执行时会报错
    MethodInvoker invoker = isAmbiguous
      ? new AmbiguousMethodInvoker(method, MessageFormat.format(
      "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
      name, method.getDeclaringClass().getName()))
      : new MethodInvoker(method);
    // 放入到 getMethods 中。key -> '属性'名称，value -> '属性'的get方法
    getMethods.put(name, invoker);
    // 获取到方法真实的返回类型
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // 放入到 getTypes 中。key -> '属性'名称，value -> '属性'的类型
    getTypes.put(name, typeToClass(returnType));
  }

  /**
   * 将 clazz 的所有 set 方法保存到 setMethods 中
   *
   * @param clazz 要保存的类
   */
  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获取类中所有的方法
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods)
      // 只保留请求参数为 1 的，并且名称看上起像是 set 方法的
      .filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      // 将可能存在冲突的方法放到 conflictingSetters 中
      .forEach(
        m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决有冲突的属性->set方法映射
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 可能出现冲突的添加方法到 conflictingMethods 中
   *
   * @param conflictingMethods 可能出现冲突方法 map。key -> 方法名称，value -> 方法列表
   * @param name               方法名称
   * @param method             方法
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name,
    Method method) {
    if (isValidPropertyName(name)) {
      // 如果属性名称有效则添加到 conflictingMethods 中
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  /**
   * 解决 conflictingSetters 中有冲突的 属性名称 -> set 方法的映射
   *
   * @param conflictingSetters 可能存在冲突的 属性名称 -> set 方法的映射
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      // 属性名称
      String propName = entry.getKey();
      // 可能存在冲突的 set 方法
      List<Method> setters = entry.getValue();
      // 通过属性名称获取其 get 方法返回的类型
      Class<?> getterType = getTypes.get(propName);
      // 如果属性的 get 方法是否存在 '唯一解'
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // 如果属性的 get 方法有 '唯一解'，并且当前 set 方法的请求参数类型等于这个 '唯一解' 的返回类型
          // 那么这个 set 方法就是 '最优解'
          match = setter;
          break;
        }
        if (!isSetterAmbiguous) {
          // 如果 set 还没有 '最优解'，则让 match 和 setter 进行比较
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }

      if (match != null) {
        // 如果找到了最优解，则放到 setMethods 中
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 在 setter1 和 setter2 之间选出最合适的 set 方法
   *
   * @param setter1  set 方法1
   * @param setter2  set 方法2
   * @param property 属性名称
   * @return 最合适的 set 方法
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }

    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      // 如果 paramType2 是 paramType1 本身或者是 paramType1 的子类
      // 那么粒度更小的 setter2 被认为是更合适的
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      // 如果 paramType1 是 paramType2 本身或者是 paramType2 的子类
      // 那么粒度更小的 setter1 被认为是更合适的
      return setter1;
    }

    // 经过了前面的筛选，还是没有找到合适 set 方法
    // 就将 setter2 方法封装为一个 错误方法，在调用的时候会抛出异常
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
      MessageFormat.format(
        "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
        property, setter2.getDeclaringClass().getName(), paramType1.getName(),
        paramType2.getName()));
    // 将方法放到 setMethods 中
    setMethods.put(property, invoker);
    // 得到 setter2 的返回值类型，并放入到 setTypes 中
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    // 由于没有找到最优解，所以返回 null
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // 如果 setMethods 不包含这个属性，则记录到 setMethods 和 setTypes 中
        int modifiers = field.getModifiers();
        // 如果是 static final 则只能由 classloader 来赋值，所以要剔除掉
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        // 如果 getMethods 不包含这个属性，则记录到 getMethods 和 getTypes 中
        addGetField(field);
      }
    }
    if (clazz.getSuperclass() != null) {
      // 自底向上，遍历父类的 field
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      // 属性名称合法的话，就放到 setMethods 和 setTypes 中
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      // 属性名称合法的话，就放到 getMethods 和 getTypes 中
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 是否是有效的属性名称
   *
   * @param name 属性名称
   * @return 是否有效
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * 获取当前类以及所有超类中声明的所有方法。 单独封装一个获取方法列表的方法，而不是使用 Class.getMethods()，是因为想要获取到 private 方法
   *
   * @param clazz 类型
   * @return 该 Class 中的方法
   */
  private Method[] getClassMethods(Class<?> clazz) {
    // key -> 方法签名，value -> 对应的方法
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;

    while (currentClass != null && currentClass != Object.class) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // 因为当前类可能是一个 abstract 的类，所以还需要搜索它实现的所有接口，abstract 类是可以不实现接口中的方法的
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        // 由于接口中定义的方法默认是 public 的，所以只需要调用 getMethods 就能拿到所有的方法
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      // 将当前类设置为它的父类，从下至上，一直搜索到当前类为 Object 为止
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) {
        // 忽略所有的桥接方法
        String signature = getSignature(currentMethod);
        // 如果 key 不存在才放到 uniqueMethods 中
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 拼接并返回一个方法的签名：ReturnType#methodName:paramTypeName1,paramTypeName2...
   *
   * @param method 方法
   * @return 签名
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException(
        "There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException(
        "There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException(
        "There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException(
        "There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
