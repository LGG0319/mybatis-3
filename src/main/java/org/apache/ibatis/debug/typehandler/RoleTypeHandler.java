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
package org.apache.ibatis.debug.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class RoleTypeHandler extends BaseTypeHandler<List<Integer>> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, List<Integer> parameter, JdbcType jdbcType)
      throws SQLException {
    if (parameter == null || parameter.isEmpty()) {
      ps.setString(i, "");
    } else {
      StringBuilder paramStr = new StringBuilder();
      for (int j = 0; j < parameter.size(); j++) {
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
