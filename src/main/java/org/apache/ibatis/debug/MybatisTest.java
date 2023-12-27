package org.apache.ibatis.debug;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;

public class MybatisTest {
    public static void main(String[] args) throws IOException {
        String resource = "mybatis-config.xml";
        // 加载mybatis的配置文件
        InputStream inputStream = Resources.getResourceAsStream(resource);
        // 获取sqlSessionFactory
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        // 获取sqlSession
        SqlSession sqlSession = sqlSessionFactory.openSession();

//        UserMapper mapper = sqlSession.getMapper(UserMapper.class);
        // 查询用户
//        User user02 = mapper.selectUserById("101");
//
//        System.out.println("user02: " + user02);

//         新增用户
//        User user = new User();
//        user.setId(105);
//        user.setName("insert");
//        user.setCreateDate(new Date());
//        user.setUpdateDate(new Date());
//        int addUser = mapper.insertUserInfo(user);
//        sqlSession.commit();
//        System.out.println("新增用户结果: " + addUser);

        // 更新用户
        //        User user03 = new User();
        //        user03.setId(105);
        //        user03.setName("insert-U");
        //        user03.setUpdateDate(new Date());
        //        int updateUser = mapper.updateUserInfo(user03);
        //        System.out.println("更新用户结果：" + updateUser);

        // 删除用户
//        User user04 = new User();
//        user04.setId(105);
//        int deleteUser = mapper.deleteUserInfo(user04);
//        System.out.println("删除用户结果：" + deleteUser);
        getUser(sqlSession);
//        insertUser(sqlSession);
    }

    private static void insertUser (SqlSession sqlSession) {
        UserMapper mapper = sqlSession.getMapper(UserMapper.class);
        User user = new User();
        user.setId(3);
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
}
