package com.aistudy.dao;

import com.aistudy.model.User;
import com.aistudy.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

public class UserDao {

    public User findByUsername(String username) {
        String sql = "SELECT id, username, password, salt, nickname, role, status, created_at FROM t_user WHERE username = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (Exception e) {
            throw new RuntimeException("查询用户失败: " + e.getMessage(), e);
        }
    }

    public long insert(User user) {
        String sql = "INSERT INTO t_user(username, password, salt, nickname, role, status) VALUES(?,?,?,?,?,?)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.username);
            ps.setString(2, user.password);
            ps.setString(3, user.salt);
            ps.setString(4, user.nickname);
            ps.setString(5, user.role == null ? "STUDENT" : user.role);
            ps.setInt(6, 1);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.id = keys.getLong(1);
                }
            }
            return user.id;
        } catch (Exception e) {
            throw new RuntimeException("新增用户失败: " + e.getMessage(), e);
        }
    }

    public long count() {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM t_user");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            throw new RuntimeException("统计用户失败: " + e.getMessage(), e);
        }
    }

    public int updateNickname(long id, String nickname) {
        String sql = "UPDATE t_user SET nickname = ? WHERE id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nickname);
            ps.setLong(2, id);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新昵称失败: " + e.getMessage(), e);
        }
    }

    public int updatePassword(long id, String password, String salt) {
        String sql = "UPDATE t_user SET password = ?, salt = ? WHERE id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, password);
            ps.setString(2, salt);
            ps.setLong(3, id);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("修改密码失败: " + e.getMessage(), e);
        }
    }

    private User map(ResultSet rs) throws Exception {
        User u = new User();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.password = rs.getString("password");
        u.salt = rs.getString("salt");
        u.nickname = rs.getString("nickname");
        u.role = rs.getString("role");
        u.status = rs.getInt("status");
        Timestamp ts = rs.getTimestamp("created_at");
        u.createdAt = ts == null ? null : new java.util.Date(ts.getTime());
        return u;
    }
}
