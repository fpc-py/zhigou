package com.zhigou.common.mybatis;

import com.zhigou.common.crypto.AmountEncryptUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * B5 金额透明加解密 TypeHandler：实体仍为 Long（分），
 * 写库加密为 `ENC:` 密文，读库自动解密——业务层无感。
 *
 * <p>用法：实体金额字段加
 * {@code @TableField(typeHandler = AmountTypeHandler.class, jdbcType = JdbcType.VARCHAR)}，
 * 对应列需为 VARCHAR（迁移 SQL 见 order-service V20261020）。</p>
 */
public class AmountTypeHandler extends BaseTypeHandler<Long> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, AmountEncryptUtil.encrypt(String.valueOf(parameter)));
    }

    @Override
    public Long getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Long getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Long getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Long parse(String raw) {
        if (raw == null) return null;
        String decrypted = AmountEncryptUtil.decrypt(raw);
        try {
            return Long.parseLong(decrypted.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("金额列解析失败（非数字密文/明文）: " + raw, e);
        }
    }
}
