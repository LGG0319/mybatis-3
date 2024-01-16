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

import java.util.HashMap;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

  // 根据id查询用户信息
  User selectUserById(String id);

  // 新增用户
  int insertUserInfo(User user);

  // 更新用户
  int updateUserInfo(User user);

  // 删除用户
  int deleteUserInfo(User user);

  int insert(@Param("user") User user);

  User getById(@Param("id") Integer id);

  HashMap<String, Object> queryForMap(@Param("id") Integer id);

  @MapKey("id")
  HashMap<String, User> queryForMaps();
}
