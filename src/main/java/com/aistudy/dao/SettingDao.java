package com.aistudy.dao;

import com.aistudy.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局设置表 t_setting 的读写。表在应用启动时由 schema.sql 创建。
 */
public class SettingDao {

    public Map<String, String> loadAll() {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT setting_key, setting_value FROM t_setting";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException("读取设置失败: " + e.getMessage(), e);
        }
    }

    public void upsert(String key, String value, Long userId) {
        String sql = "INSERT INTO t_setting(setting_key, setting_value, updated_by) VALUES(?,?,?) "
                + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_by = VALUES(updated_by)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            if (userId == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, userId);
            }
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("保存设置失败: " + e.getMessage(), e);
        }
    }

    public void delete(String key) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM t_setting WHERE setting_key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除设置失败: " + e.getMessage(), e);
        }
    }

    public void deleteAll() {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM t_setting")) {
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("重置设置失败: " + e.getMessage(), e);
        }
    }
}
