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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * @author Clinton Begin 专门处理数据库汇报的组件，封装了JDBC Statement操作，负责对JDBC statement 的操作
 *         最主要的作用在于创建Statement对象与数据库进行交流，还会使用ParameterHandler进行参数配置，使用ResultSetHandler把查询结果与实体类进行绑定
 */
public interface StatementHandler {
  // 声明Statement,通过JDBC中的Connection声明
  Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException;

  // 设置参数，只有JDBC中PreparedStatement 才有该行为
  void parameterize(Statement statement) throws SQLException;

  void batch(Statement statement) throws SQLException;

  int update(Statement statement) throws SQLException;

  <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException;

  // 游标查询
  <E> Cursor<E> queryCursor(Statement statement) throws SQLException;

  // 获取当前操作的动态SQL
  BoundSql getBoundSql();

  // 获取当前操作的参数处理器
  ParameterHandler getParameterHandler();

}
