package com.aistudy.dao;

import com.aistudy.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;

public class UploadDao {

    public long insert(long userId, String originalName, String storedPath, String contentType, long size) {
        String sql = "INSERT INTO t_upload(user_id, original_name, stored_path, content_type, file_size) VALUES(?,?,?,?,?)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, originalName);
            ps.setString(3, storedPath);
            ps.setString(4, contentType);
            ps.setLong(5, size);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("保存上传记录失败: " + e.getMessage(), e);
        }
    }
}
