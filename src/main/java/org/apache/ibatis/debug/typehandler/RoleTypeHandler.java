package org.apache.ibatis.debug.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RoleTypeHandler extends BaseTypeHandler<List<Integer>> {


    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Integer> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null || parameter.isEmpty()) {
            ps.setString(i, "");
        } else {
            StringBuilder paramStr = new StringBuilder();
            for (int j = 0; j < parameter.size(); j ++) {
                paramStr.append(parameter.get(j));
                if (j != parameter.size() - 1) {
                    paramStr.append(",");
                }
            }
            ps.setString(i, paramStr.toString());
        }
    }

    @Override
    public List<Integer> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.convertToList(rs.getString(columnName));
    }

    @Override
    public List<Integer> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.convertToList(rs.getString(columnIndex));
    }

    @Override
    public List<Integer> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.convertToList(cs.getString(columnIndex));
    }

    private List<Integer> convertToList(String rolesStr) {
        List<Integer> roleList = new ArrayList<>();
        if (!"".equals(rolesStr)) {
            String[] roleIdStrArray = rolesStr.split(",");
            for (String roleIdStr : roleIdStrArray) {
                roleList.add(Integer.parseInt(roleIdStr));
            }
        }

        return roleList;
    }
}
