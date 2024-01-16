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
package org.apache.ibatis.debug;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

public class MybatisTest {
  public static void main(String[] args) throws IOException {
    String resource = "mybatis-config.xml";
    // 加载mybatis的配置文件
    InputStream inputStream = Resources.getResourceAsStream(resource);
    // 获取sqlSessionFactory
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    // 获取sqlSession
    SqlSession sqlSession = sqlSessionFactory.openSession();

    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    // 查询用户
    User user02 = mapper.selectUserById("3");

    System.out.println("user02: " + user02);

    // 新增用户
    // User user = new User();
    // user.setId(105);
    // user.setName("insert");
    // user.setCreateDate(new Date());
    // user.setUpdateDate(new Date());
    // int addUser = mapper.insertUserInfo(user);
    // sqlSession.commit();
    // System.out.println("新增用户结果: " + addUser);

    // 更新用户
    // User user03 = new User();
    // user03.setId(105);
    // user03.setName("insert-U");
    // user03.setUpdateDate(new Date());
    // int updateUser = mapper.updateUserInfo(user03);
    // System.out.println("更新用户结果：" + updateUser);

    // 删除用户
    // User user04 = new User();
    // user04.setId(105);
    // int deleteUser = mapper.deleteUserInfo(user04);
    // System.out.println("删除用户结果：" + deleteUser);
    // getUser(sqlSession);
    // insertUser(sqlSession);
    // getUserForMap(sqlSession);
  }

  private static void insertUser(SqlSession sqlSession) {
    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    User user = new User();
    user.setId(2);
    user.setName("张三");
    user.setRole(Arrays.asList(1, 2));
    mapper.insert(user);
    // 提交事务
    sqlSession.commit();
    sqlSession.close();
  }

  private static void getUser(SqlSession sqlSession) {
    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    User user = mapper.getById(3);
    System.out.println(user);

    sqlSession.close();
  }

  private static void getUserForMap(SqlSession sqlSession) {
    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    // HashMap<String, Object> map = mapper.queryForMap(3);
    HashMap<String, User> map = mapper.queryForMaps();

    System.out.println(map.get(1));

    sqlSession.close();
  }
}
