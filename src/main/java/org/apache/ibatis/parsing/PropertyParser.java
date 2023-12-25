/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * 占位符处理
 */
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   * The default value is {@code false} (indicate disable a default value on placeholder) If you specify the
   * {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   *
   * @since 3.4.2
   * 通过这个配置设置是否支持默认值
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   * The default separator is {@code ":"}.
   * </p>
   *
   * @since 3.4.2
   * 通过这个配置自定义分隔符
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

    // 默认是否支持默认值为false
  private static final String ENABLE_DEFAULT_VALUE = "false";
    // 默认分隔符为:
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
      // 创建VariableTokenHandler对象(负责从Properties中获取占位符所代表的值)
    VariableTokenHandler handler = new VariableTokenHandler(variables);
      // 创建GenericTokenParser对象(负责解析出占位符中的字面值)
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
      // 处理占位符(解析出字符串中的占位符，然后调用)
    return parser.parse(string);
  }

  private static class VariableTokenHandler implements TokenHandler {
      // 配置文件中的Properties
    private final Properties variables;
      // 是否支持默认值
    private final boolean enableDefaultValue;
      // 分隔符
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
        // 获取是否支持默认值配置
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
        // 获取分隔符
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

      // 根据key查找对应的value   如果不存在返回默认值
    private String getPropertyValue(String key, String defaultValue) {
      return variables == null ? defaultValue : variables.getProperty(key, defaultValue);
    }

      // 获取到其对应的值
    @Override
    public String handleToken(String content) {
        // 判断Properties是否为空
      if (variables != null) {
        String key = content;
          // 是否支持默认值
        if (enableDefaultValue) {
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          if (separatorIndex >= 0) {
              // 分隔符前是Properties中的key
            key = content.substring(0, separatorIndex);
              // 分隔符后是默认值
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
            // 默认值存在，先从Properties中查找   没有找到则返回默认值
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue);
          }
        }
          // 如果存在这个配置则返回
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      return "${" + content + "}";
    }
  }

}
