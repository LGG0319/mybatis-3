package org.apache.ibatis.debug;

public interface UserMapper {

    // 根据id查询用户信息
    User selectUserById(String id);

    // 新增用户
    int insertUserInfo(User user);

    // 更新用户
    int updateUserInfo(User user);

    // 删除用户
    int deleteUserInfo(User user);
}
