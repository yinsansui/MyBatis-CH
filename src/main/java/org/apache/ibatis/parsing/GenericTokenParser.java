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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
public class GenericTokenParser {
  // 起始符号
  private final String openToken;
  // 结束符号
  private final String closeToken;
  // 处理器
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // 预先查找起始符号
    int start = text.indexOf(openToken);
    // 如果没有找到则直接返回原文本
    if (start == -1) {
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;
    do {
      if (start > 0 && src[start - 1] == '\\') {
        // 起始符号被转义了，删除反斜杠然后然后开始下标向后移动
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        // 先拼接 "${" 之前内容
        builder.append(src, offset, start - offset);
        // 将下标偏移到 "${" 紧接的字符的下标
        offset = start + openToken.length();
        // 找到了起始符号，现在需要从 offset 开始找到结束符号
        int end = text.indexOf(closeToken, offset);
        // 如果找到了结束符号
        while (end > -1) {
          // 如果找到的结束符号修饰了转义符号，则找下一个结束符号
          if (end > offset && src[end - 1] == '\\') {
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            // 找到了正确的结束符号，推出循环
            expression.append(src, offset, end - offset);
            break;
          }
        }
        if (end == -1) {
          // 没有找到结束符号，直接把剩下的内容拷贝到结果中
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          // 开始符号有对应的结束符号，则把它们中的内容交给处理器处理
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      start = text.indexOf(openToken, offset);
    } while (start > -1);
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
