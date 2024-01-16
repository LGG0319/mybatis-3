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
package org.apache.ibatis.debug.plugin;

import java.sql.Connection;
import java.util.Properties;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;

@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }) })
public class MyPlugin implements Interceptor {

  // 方法拦截
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    // 通过StatementHandler获取执行的sql
    StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
    BoundSql boundSql = statementHandler.getBoundSql();
    String sql = boundSql.getSql();

    long start = System.currentTimeMillis();
    Object proceed = invocation.proceed();
    long end = System.currentTimeMillis();

    System.out.println("本次数据库操作是慢查询，sql是:" + sql);

    return proceed;
  }

  // 获取到拦截的对象，底层也是通过代理实现的，实际上是拿到一个目标代理对象
  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  // 获取设置的阈值等参数
  @Override
  public void setProperties(Properties properties) {
    Interceptor.super.setProperties(properties);
  }

}
