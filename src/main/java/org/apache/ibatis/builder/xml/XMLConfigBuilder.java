/*
 *    Copyright 2009-2024 the original author or authors.
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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  // 是否已经解析过mybatis配置文件
  private boolean parsed;

  // XPathParser对象，Mybatis中的解析器，为XML文件的解析提供了支持
  private final XPathParser parser;

  // 环境代号
  private String environment;

  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(Configuration.class, reader, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, Reader reader, String environment,
      Properties props) {
    this(configClass, new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  // 加载解析mybatis配置文件
  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(Configuration.class, inputStream, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, InputStream inputStream, String environment,
      Properties props) {
    this(configClass, new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  // 根据inputStream加载并解析配置文件
  private XMLConfigBuilder(Class<? extends Configuration> configClass, XPathParser parser, String environment,
      Properties props) {
    super(newConfig(configClass));
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  // 解析配置文件
  public Configuration parse() {
    // 是否已经解析过mybatis配置文件
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    // 解析<configuration>节点
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      // 解析<properties>节点 （将properties节点的数据解析成一个个的键值对存放到Properties对象中）
      propertiesElement(root.evalNode("properties"));
      // 解析<settings>节点 （setting节点解析会将setting节点的子节点的name和value属性组成键值对，存放到一个Properties对象中）
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 设置Configuration类中vfsImpl字段属性(添加自定义虚拟文件系统实现类)
      loadCustomVfsImpl(settings);
      // 设置Configuration类中logImpl字段属性(添加自定义日志实现类)
      loadCustomLogImpl(settings);
      // 解析<typeAliases>节点 （typeAliass节点的解析，会将我们配置的类型别名进行注册）
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析<plugins>节点 （plugins用来解析我们配置的插件，会将解析出的插件对象存储到Configuration.interceptorChain属性中）
      pluginsElement(root.evalNode("plugins"));
      // 解析<objectFactory>节点
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析<objectWrapperFactory>节点 (扩展使用，允许用户自定义包装对象ObjectWrapper)
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析<reflectorFactory>节点
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析<environments>节点 （根据不同的环境进行配置不同的数据库，这些配置会放到environments节点下）
      environmentsElement(root.evalNode("environments"));
      // 解析<databaseIdProvider>节点（databaseIdProvider：数据库厂商表示）
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析<typeHandlers>节点 （typeHandlers中定义了我们自定义的类型转换器，解析这个节点会将我们配置的类型转换器添加到TypeHandlerRegistry中）
      typeHandlersElement(root.evalNode("typeHandlers"));
      // 解析<mappers>节点 （mappers节点中定义了我们的Mapper位置，Mybatis通过解析这个标签，将Mapper注册到MapperRegistry对象中）
      mappersElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // XNode中的方法 获取子节点的name和value属性 组成键值对
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 获取Configuration类的MetaClass对象
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 遍历解析出的键值对
    for (Object key : props.keySet()) {
      // 判断Configuration类中是否存在该属性的setter方法 不存在抛出异常
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException(
            "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfsImpl(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value == null) {
      return;
    }
    String[] clazzes = value.split(",");
    for (String clazz : clazzes) {
      if (!clazz.isEmpty()) {
        @SuppressWarnings("unchecked")
        Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
        configuration.setVfsImpl(vfsImpl);
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  private void typeAliasesElement(XNode context) {
    if (context == null) {
      return;
    }
    // 获取<typeAliases>的子节点
    for (XNode child : context.getChildren()) {
      // 如果是<package>节点
      if ("package".equals(child.getName())) {
        // 获取name属性
        String typeAliasPackage = child.getStringAttribute("name");
        // 按照包名注册配置好的类型别名
        configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
      } else {
        // 获取别名
        String alias = child.getStringAttribute("alias");
        // 获取类型
        String type = child.getStringAttribute("type");
        try {
          // 通过反射加载类
          Class<?> clazz = Resources.classForName(type);
          // 如果别名为空，使用mybatis的默认名 类名
          if (alias == null) {
            typeAliasRegistry.registerAlias(clazz);
          } else {
            // 调用通过别名和类型的注册方法
            typeAliasRegistry.registerAlias(alias, clazz);
          }
        } catch (ClassNotFoundException e) {
          throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
        }
      }
    }
  }

  private void pluginsElement(XNode context) throws Exception {
    // 如果节点不存在则不处理
    if (context != null) {
      // 遍历子节点
      for (XNode child : context.getChildren()) {
        // 获取interceptor属性
        String interceptor = child.getStringAttribute("interceptor");
        // 获取子节点配置的属性
        Properties properties = child.getChildrenAsProperties();
        // 通过反射创建类 会先从TypeAliasRegistry中查找 不存在使用反射加载
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor()
            .newInstance();
        // 设置属性
        interceptorInstance.setProperties(properties);
        // 将解析出的Interceptor对象添加到InterceptorChain对象中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取type属性
      String type = context.getStringAttribute("type");
      // 获取配置的属性
      Properties properties = context.getChildrenAsProperties();
      // 创建对象
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置properties
      factory.setProperties(properties);
      // 存储到Configuration.objectFactory属性中
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取type属性
      String type = context.getStringAttribute("type");
      // 创建对象
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 存储到Configuration.objectWrapperFactory属性中
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取type属性
      String type = context.getStringAttribute("type");
      // 创建对象
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 存储到Configuration.reflectorFactory属性中
      configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    // XNode中的方法，会将子节点的name和value字段存储到Properties对象中
    Properties defaults = context.getChildrenAsProperties();
    // 获取<properties>节点的resource和url属性
    String resource = context.getStringAttribute("resource");
    String url = context.getStringAttribute("url");
    // 两个不可同时存在，否则抛出异常
    if (resource != null && url != null) {
      throw new BuilderException(
          "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
    }
    // 加载properties文件中的配置
    if (resource != null) {
      defaults.putAll(Resources.getResourceAsProperties(resource));
    } else if (url != null) {
      defaults.putAll(Resources.getUrlAsProperties(url));
    }
    // 获取variables属性的值（variables ： configuration中的Properties属性）
    Properties vars = configuration.getVariables();
    if (vars != null) {
      defaults.putAll(vars);
    }
    // 更新XPathParser和Configuration中的variables属性
    parser.setVariables(defaults);
    configuration.setVariables(defaults);
  }

  private void settingsElement(Properties props) {
    configuration
        .setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(
        AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(
        stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
    configuration.setArgNameBasedConstructorAutoMapping(
        booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
    configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
    configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    if (environment == null) {
      // environment如果为空 取default属性配置的值
      environment = context.getStringAttribute("default");
    }
    // 遍历子节点
    for (XNode child : context.getChildren()) {
      // 获取id属性
      String id = child.getStringAttribute("id");
      // 判断环境代号是否为environment的值
      if (isSpecifiedEnvironment(id)) {
        // 获取TransactionFactory对象
        TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
        // 获取DataSourceFactory对象
        DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
        // 获取DataSource对象
        DataSource dataSource = dsFactory.getDataSource();
        // 创建Environment.Builder对象
        Environment.Builder environmentBuilder = new Environment.Builder(id).transactionFactory(txFactory)
            .dataSource(dataSource);
        // 设置configuration.environment属性
        configuration.setEnvironment(environmentBuilder.build());
        break;
      }
    }
  }

  /**
   * 可以根据不同的数据库厂商执行不同的语句。 比如在公司开发使用的数据库都是PG(Postgresql)，但是客户要求使用MySql。 可以使用databaseIdProvider这个元素实现数据库兼容不同厂商。
   * 多厂商的支持是基于映射语句中的databaseId属性
   * MyBatis会加载带有匹配当前数据库databaseId属性和所有不带databaseId属性的语句。如果同时找到带有databaseId和不带databaseId的相同语句，则后者会被舍弃。
   * 使用多数据库SQL时需要配置databaseIdProvider属性。 当databaseId属性被配置的时候，系统会优先获取和数据库配置一致的SQL， 否则取没有配置databaseId的SQL,可以把它当默认值；
   * 如果还是取不到，就会抛出异常。 配置方式： name：数据库名称 value：数据库别名 <databaseIdProvider type="DB_VENDOR">
   * <property name="SQL Server" value="sqlserver"/> <property name="DB2" value="db2"/>
   * <property name="Oracle" value="oracle" /> </databaseIdProvider>
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    String type = context.getStringAttribute("type");
    // awful patch to keep backward compatibility
    if ("VENDOR".equals(type)) {
      type = "DB_VENDOR";
    }
    // XNode中的方法 获取子节点的name和value属性 组成键值对
    Properties properties = context.getChildrenAsProperties();
    // 生成DatabaseIdProvider对象
    DatabaseIdProvider databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor()
        .newInstance();
    // 设置properties
    databaseIdProvider.setProperties(properties);
    // 从configuration中获取environment属性
    Environment environment = configuration.getEnvironment();
    if (environment != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      // 根据环境的数据源设置要使用的数据库
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 获取type属性
      String type = context.getStringAttribute("type");
      // 获取配置的属性
      Properties props = context.getChildrenAsProperties();
      // 创建DataSourceFactory对象
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置properties
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlersElement(XNode context) {
    if (context == null) {
      return;
    }
    // 遍历子节点
    for (XNode child : context.getChildren()) {
      // 如果是<package>节点
      if ("package".equals(child.getName())) {
        // 获取配置的包名
        String typeHandlerPackage = child.getStringAttribute("name");
        // 通过包名注册类型转换器
        typeHandlerRegistry.register(typeHandlerPackage);
      } else {
        // 获取配置的javaType、jdbcType和handler属性
        String javaTypeName = child.getStringAttribute("javaType");
        String jdbcTypeName = child.getStringAttribute("jdbcType");
        String handlerTypeName = child.getStringAttribute("handler");
        // 获取javaType对应的Class对象
        Class<?> javaTypeClass = resolveClass(javaTypeName);
        // 获取JdbcType
        JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
        // 获取handler对应的Class文件
        Class<?> typeHandlerClass = resolveClass(handlerTypeName);
        if (javaTypeClass != null) {
          // 注册类型转换器
          if (jdbcType == null) {
            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
          } else {
            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
          }
        } else {
          typeHandlerRegistry.register(typeHandlerClass);
        }
      }
    }
  }

  // 解析mappers节点，将mapper.xml文件加入Configuration对象中
  private void mappersElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    for (XNode child : context.getChildren()) {
      // 如果是package节点
      if ("package".equals(child.getName())) {
        // 获取节点的name属性
        String mapperPackage = child.getStringAttribute("name");
        // 通过包名注册mapper
        configuration.addMappers(mapperPackage);
      } else {
        // 获取resource属性
        String resource = child.getStringAttribute("resource");
        // 获取url属性
        String url = child.getStringAttribute("url");
        // 获取class属性
        String mapperClass = child.getStringAttribute("class");
        // 如果recourse属性不为空 其他两个属性为空
        if (resource != null && url == null && mapperClass == null) {
          ErrorContext.instance().resource(resource);
          // 加载Mapper映射文件
          try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            // 创建XMLMapperBuilder对象
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                configuration.getSqlFragments());
            // 解析映射文件
            mapperParser.parse();
          }
          // 如果url属性不为空，其他两个属性为空
        } else if (resource == null && url != null && mapperClass == null) {
          ErrorContext.instance().resource(url);
          // 加载映射文件
          try (InputStream inputStream = Resources.getUrlAsStream(url)) {
            // 创建XMLMapperBuilder对象
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                configuration.getSqlFragments());
            // 解析映射文件
            mapperParser.parse();
          }
          // 如果class属性不为空 其他两个属性为空
        } else if (resource == null && url == null && mapperClass != null) {
          // 获取mapper接口类型
          Class<?> mapperInterface = Resources.classForName(mapperClass);
          // 直接加载映射文件
          configuration.addMapper(mapperInterface);
        } else {
          // 如果同时配置了多个属性则抛出异常
          throw new BuilderException(
              "A mapper element may only specify a url, resource or class, but not more than one.");
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    }
    if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    }
    return environment.equals(id);
  }

  private static Configuration newConfig(Class<? extends Configuration> configClass) {
    try {
      return configClass.getDeclaredConstructor().newInstance();
    } catch (Exception ex) {
      throw new BuilderException("Failed to create a new Configuration instance.", ex);
    }
  }

}
