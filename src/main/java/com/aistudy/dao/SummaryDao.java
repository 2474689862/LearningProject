package com.aistudy.dao;

import com.aistudy.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学习总结存储：每个用户只保留一份最新的 AI 总结，
 * 只有用户主动点「生成总结」时才覆盖，进入页面直接读这份缓存。
 */
public class SummaryDao {

    private static final String COLUMNS =
            "text, stats_json, provider, model, provider_label, elapsed_ms, fallback, log_id, updated_at";

    /** 读取该用户已保存的总结，没有则返回 null */
    public Map<String, Object> findLatest(long userId) {
        String sql = "SELECT " + COLUMNS + " FROM t_summary WHERE user_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("text", rs.getString("text"));
                m.put("statsJson", rs.getString("stats_json"));
                m.put("provider", rs.getString("provider"));
                m.put("model", rs.getString("model"));
                m.put("providerLabel", rs.getString("provider_label"));
                m.put("elapsedMs", rs.getInt("elapsed_ms"));
                m.put("fallback", rs.getInt("fallback") == 1);
                long logId = rs.getLong("log_id");
                m.put("logId", rs.wasNull() ? null : logId);
                Timestamp ts = rs.getTimestamp("updated_at");
                m.put("updatedAt", ts == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(ts));
                m.put("updatedAtMillis", ts == null ? null : ts.getTime());
                return m;
            }
        } catch (Exception e) {
            throw new RuntimeException("读取学习总结失败: " + e.getMessage(), e);
        }
    }

    /** 保存（覆盖）该用户的总结 */
    public void save(long userId, String text, String statsJson, String provider, String model,
                     String providerLabel, long elapsedMs, boolean fallback, Long logId) {
        String sql = "INSERT INTO t_summary(user_id, text, stats_json, provider, model, provider_label, "
                + "elapsed_ms, fallback, log_id) VALUES(?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE text = VALUES(text), stats_json = VALUES(stats_json), "
                + "provider = VALUES(provider), model = VALUES(model), provider_label = VALUES(provider_label), "
                + "elapsed_ms = VALUES(elapsed_ms), fallback = VALUES(fallback), log_id = VALUES(log_id)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, text);
            ps.setString(3, statsJson);
            ps.setString(4, provider);
            ps.setString(5, model);
            ps.setString(6, providerLabel);
            ps.setLong(7, elapsedMs);
            ps.setInt(8, fallback ? 1 : 0);
            if (logId == null) {
                ps.setNull(9, java.sql.Types.BIGINT);
            } else {
                ps.setLong(9, logId);
            }
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("保存学习总结失败: " + e.getMessage(), e);
        }
    }

    /** 清空该用户的总结（重新生成失败或题量归零时使用） */
    public void clear(long userId) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM t_summary WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("清空学习总结失败: " + e.getMessage(), e);
        }
    }
}
