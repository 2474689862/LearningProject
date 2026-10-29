package com.aistudy.dao;

import com.aistudy.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 调用日志读写。
 */
public class AiDao {

    public long insert(long userId, Long questionId, String scene, String provider, String model,
                       String prompt, String answer, long elapsedMs, boolean success) {
        String sql = "INSERT INTO t_ai_log(user_id, question_id, scene, provider, model, prompt, answer, elapsed_ms, success) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            if (questionId == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, questionId);
            }
            ps.setString(3, scene);
            ps.setString(4, provider);
            ps.setString(5, model);
            ps.setString(6, prompt);
            ps.setString(7, answer);
            ps.setLong(8, elapsedMs);
            ps.setInt(9, success ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("写入 AI 日志失败: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> recent(long userId, int limit) {
        String sql = "SELECT id, question_id, scene, provider, model, elapsed_ms, created_at "
                + "FROM t_ai_log WHERE user_id = ? ORDER BY id DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    long qid = rs.getLong("question_id");
                    row.put("questionId", rs.wasNull() ? null : qid);
                    row.put("scene", rs.getString("scene"));
                    row.put("provider", rs.getString("provider"));
                    row.put("model", rs.getString("model"));
                    row.put("elapsedMs", rs.getInt("elapsed_ms"));
                    row.put("createdAt", rs.getTimestamp("created_at"));
                    list.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询 AI 日志失败: " + e.getMessage(), e);
        }
        return list;
    }
}
