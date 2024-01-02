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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

    // XPathParser对象  解析器
  private final XPathParser parser;
    // 映射文件解析辅助类
  private final MapperBuilderAssistant builderAssistant;
    // sql片段
  private final Map<String, XNode> sqlFragments;
    // mapper文件地址
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments,
      String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()), configuration,
        resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()), configuration,
        resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource,
      Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  public void parse() {
      // 判断是否已经加载过该映射文件
    if (!configuration.isResourceLoaded(resource)) {
        // 处理mapper节点
      configurationElement(parser.evalNode("/mapper"));
        // 将映射文件记录到Configuration.loadedResources字段中   是一个Set
      configuration.addLoadedResource(resource);
        // 注册Mapper
      bindMapperForNamespace();
    }
      // 处理解析失败的ResultMap
    parsePendingResultMaps();
      // 处理解析失败的cache-ref
    parsePendingCacheRefs();
      // 处理解析失败的SQ
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  // 解析Mapper.xml文件
  private void configurationElement(XNode context) {
    try {
        // 获取namespace属性
      String namespace = context.getStringAttribute("namespace");
        // namespace不存在抛出异常
      if (namespace == null || namespace.isEmpty()) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
        // 设置builderAssistant字段的currentNamespace属性
      builderAssistant.setCurrentNamespace(namespace);
        // 解析cache-ref节点 （cache-ref标签可以为当前mapper文件引入其他mapper的二级缓存）
      cacheRefElement(context.evalNode("cache-ref"));
        // 解析cache节点 （cache标签是用来配置二级缓存的）
        // 【cache和cache-ref节点的解析最后都会修改MapperBuilderAssistant.currentCache属性的值，因此，当这两个标签都存在时会存在覆盖的问题。】
      cacheElement(context.evalNode("cache"));
        // 解析mapper下parameterMap节点【标签已经被废弃】
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
        // 解析mapper下resultMap节点 【Mybatis对这个节点的解析，会将一条条的映射关系解析成一个个的ResultMapping对象，将整个resultMap解析成ResultMap对象】
      resultMapElements(context.evalNodes("/mapper/resultMap"));
        // 解析mapper下sql节点 【sql的节点解析是添加到sqlFragments属性中】
      sqlElement(context.evalNodes("/mapper/sql"));
        // 解析select|insert|update|delete节点  【是解析我们写的SQL语句节点的入口】
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

    // 是解析我们写的SQL语句节点的入口
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
        // 创建XMLStatementBuilder对象
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context,
          requiredDatabaseId);
      try {
          // 解析StatementNode
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  //    cache-ref标签可以为当前mapper文件引入其他mapper的二级缓存
  private void cacheRefElement(XNode context) {
    if (context != null) {
        // Configuration.cacheRefMap属性中添加数据   当前命名空间和节点的namespace属性进行关联
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
        // 创建CacheRefResolver对象
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant,
          context.getStringAttribute("namespace"));
      try {
          // 解析cacheRef
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
          // 如果解析发生异常添加到incompleteCacheRef字段中
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  private void cacheElement(XNode context) {
    if (context != null) {
        // 获取type属性  默认为PERPETUAL
      String type = context.getStringAttribute("type", "PERPETUAL");
        // 通过别名获取对应的类型
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
        // 获取eviction属性(缓存回收策略)  默认为LRU（最近最少使用策略）
      String eviction = context.getStringAttribute("eviction", "LRU");
        // 获取eviction别名对应的类型
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
        // 获取flushInterval属性（刷新间隔） 任意正整数，毫秒形式的时间段。默认不设置，缓存仅仅调用语句时刷新
      Long flushInterval = context.getLongAttribute("flushInterval");
        // 获取size属性（引用数目） 任意正整数，要记住你缓存的对象数目和你运行环境的可用内存资源数目。默认值是1024
      Integer size = context.getIntAttribute("size");
        // 获取redaOnly属性  默认为false
        // 只读的缓存会给所有调用者返回缓存对象的相同实例。因此这些对象不能被修改。这提供了很重要的性能优势。
        // 可读写的缓存会返回缓存对象的拷贝（通过序列化）这会慢一些，但是安全
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
        // 获取blocking属性  默认为false
      boolean blocking = context.getBooleanAttribute("blocking", false);
        // 获取子节点数据
      Properties props = context.getChildrenAsProperties();
        // 创建Cache对象并添加到Configuration.caches中
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property,
            javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

//    解析mapper下resultMap节点
  private void resultMapElements(List<XNode> list) {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings,
      Class<?> enclosingType) {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
      // 获取标签的类型属性
    String type = resultMapNode.getStringAttribute("type", resultMapNode.getStringAttribute("ofType",
        resultMapNode.getStringAttribute("resultType", resultMapNode.getStringAttribute("javaType"))));
      // 获取到类型的Class对象
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
      // ResultMapping列表  会将子节点解析成一个个的ResultMapping
    List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
        // 解析constructor节点   这个节点用于配置构造方法所需的属性
      if ("constructor".equals(resultChild.getName())) {
          // 解析constructor节点的子节点
        processConstructorElement(resultChild, typeClass, resultMappings);
          // 解析discriminator节点
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
          // 标记列表
        List<ResultFlag> flags = new ArrayList<>();
          // 如果是id标签加上ResultFlag.ID标签
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
          // 解析出子节点对应的ResultMapping对象
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
      // 获取id属性
    String id = resultMapNode.getStringAttribute("id", resultMapNode.getValueBasedIdentifier());
      // 获取extends属性
    String extend = resultMapNode.getStringAttribute("extends");
      // 获取autoMapping属性
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
      // 创建ResultMapResolver对象
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator,
        resultMappings, autoMapping);
    try {
        // 创建ResultMap对象并返回
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {
        // 如果解析失败，将resultMapResolver添加到Configuration.incompleteResultMap中  在后面进行处理
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
    List<XNode> argChildren = resultChild.getChildren();
      // 遍历子节点
    for (XNode argChild : argChildren) {
        // 标记列表
      List<ResultFlag> flags = new ArrayList<>();
        // 添加CONSTRUCTOR标记
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
          // 如果是idArg节点  添加ID标记
        flags.add(ResultFlag.ID);
      }
        // 解析出子节点对应的ResultMapping对象
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  // 用来解析discriminator节点
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType,
      List<ResultMapping> resultMappings) {
      // 获取column属性
    String column = context.getStringAttribute("column");
      // 获取javaType属性
    String javaType = context.getStringAttribute("javaType");
      // 获取jdbcType属性
    String jdbcType = context.getStringAttribute("jdbcType");
      // 获取typeHandler属性
    String typeHandler = context.getStringAttribute("typeHandler");
      // 获取javaType对应的Class对象
    Class<?> javaTypeClass = resolveClass(javaType);
      // 获取typeHandler对应的Class对象
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap",
          processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass,
        discriminatorMap);
  }

  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      String databaseId = context.getStringAttribute("databaseId");
        // 获取id属性值
      String id = context.getStringAttribute("id");
        // 在id属性值前加上namespace
      id = builderAssistant.applyCurrentNamespace(id, false);
        // 校验databaseId是否匹配
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
          // 存储到sqlFragments中
        sqlFragments.put(id, context);
      }
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    XNode context = this.sqlFragments.get(id);
    return context.getStringAttribute("databaseId") == null;
  }

  // 用来创建ResultMapping对象
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
        // 如果是Constructor节点下的节点  获取name属性
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
        // 获取column属性
    String column = context.getStringAttribute("column");
      // 获取javaType属性
    String javaType = context.getStringAttribute("javaType");
      // 获取jdbcType属性
    String jdbcType = context.getStringAttribute("jdbcType");
      // 获取select属性
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
        () -> processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
      // 是否支持懒加载
    boolean lazy = "lazy"
        .equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
      // 解析出javaType对应的Class对象
    Class<?> javaTypeClass = resolveClass(javaType);
      // 解析出typeHandler对应的Class对象
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
      // 获取到对应的JdbcType
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
      // 创建ResultMapping对象
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect,
        nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings,
      Class<?> enclosingType) {
    if (Arrays.asList("association", "collection", "case").contains(context.getName())
        && context.getStringAttribute("select") == null) {
      validateCollection(context, enclosingType);
      ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
      return resultMap.getId();
    }
    return null;
  }

  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
            "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  // 根据nameSpace绑定mapper接口
  private void bindMapperForNamespace() {
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        // ignore, bound type is not required
      }
      if (boundType != null && !configuration.hasMapper(boundType)) {
        // Spring may not know the real resource name so we set a flag
        // to prevent loading again this resource from the mapper interface
        // look at MapperAnnotationBuilder#loadXmlResource
        configuration.addLoadedResource("namespace:" + namespace);
        configuration.addMapper(boundType);
      }
    }
  }

}
