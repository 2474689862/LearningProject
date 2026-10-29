package com.aistudy.dao;

import com.aistudy.model.PageResult;
import com.aistudy.model.Question;
import com.aistudy.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuestionDao {

    private static final String BASE_SELECT =
            "SELECT q.id, q.user_id, q.subject_id, q.category_id, q.title, q.content, q.image_path, "
                    + "q.q_type, q.options, q.correct_answer, "
                    + "q.answer, q.analysis, q.source, q.difficulty, q.status, q.created_at, q.updated_at, "
                    + "s.name AS subject_name, c.name AS category_name "
                    + "FROM t_question q "
                    + "JOIN t_subject s ON s.id = q.subject_id "
                    + "LEFT JOIN t_category c ON c.id = q.category_id ";

    public long insert(Question q) {
        String sql = "INSERT INTO t_question(user_id, subject_id, category_id, title, content, image_path, "
                + "q_type, options, correct_answer, answer, analysis, source, difficulty, status) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, q.userId);
            ps.setInt(2, q.subjectId);
            if (q.categoryId == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, q.categoryId);
            }
            ps.setString(4, q.title);
            ps.setString(5, q.content);
            ps.setString(6, q.imagePath);
            ps.setString(7, q.type == null ? "essay" : q.type);
            ps.setString(8, q.options);
            ps.setString(9, q.correctAnswer);
            ps.setString(10, q.answer);
            ps.setString(11, q.analysis);
            ps.setString(12, q.source);
            ps.setInt(13, q.difficulty == null ? 2 : q.difficulty);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("保存题目失败: " + e.getMessage(), e);
        }
    }

    public PageResult<Question> list(long userId, Integer subjectId, Integer categoryId, String keyword,
                                     String type, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE q.user_id = ? AND q.status = 1 ");
        args.add(userId);
        if (subjectId != null && subjectId > 0) {
            where.append("AND q.subject_id = ? ");
            args.add(subjectId);
        }
        if (categoryId != null && categoryId > 0) {
            where.append("AND q.category_id = ? ");
            args.add(categoryId);
        }
        if (type != null && !type.trim().isEmpty()) {
            where.append("AND q.q_type = ? ");
            args.add(type.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append("AND (q.title LIKE ? OR q.content LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }

        long total = 0;
        String countSql = "SELECT COUNT(*) FROM t_question q " + where;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    total = rs.getLong(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("统计题目失败: " + e.getMessage(), e);
        }

        List<Question> list = new ArrayList<>();
        String sql = BASE_SELECT + where + "ORDER BY q.id DESC LIMIT ?, ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object arg : args) {
                ps.setObject(idx++, arg);
            }
            ps.setInt(idx++, (page - 1) * size);
            ps.setInt(idx, size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("查询题目失败: " + e.getMessage(), e);
        }
        return new PageResult<>(total, page, size, list);
    }

    public Question findById(long id, long userId) {
        String sql = BASE_SELECT + "WHERE q.id = ? AND q.user_id = ? AND q.status = 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (Exception e) {
            throw new RuntimeException("查询题目详情失败: " + e.getMessage(), e);
        }
    }

    public int softDelete(long id, long userId) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE t_question SET status = 0 WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除题目失败: " + e.getMessage(), e);
        }
    }

    public long countByCategory(int categoryId) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM t_question WHERE category_id = ? AND status = 1")) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("统计分类题目失败: " + e.getMessage(), e);
        }
    }

    /** 保存（或覆盖）题目解析，用于把 AI 讲解写回错题本 */
    /** 编辑已有错题：仅更新用户自己的、未删除的题目；图片沿用原值 */
    public int update(Question q, long userId) {
        String sql = "UPDATE t_question SET subject_id = ?, category_id = ?, title = ?, content = ?, "
                + "q_type = ?, options = ?, correct_answer = ?, "
                + "answer = ?, analysis = ?, source = ?, difficulty = ? WHERE id = ? AND user_id = ? AND status = 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, q.subjectId);
            if (q.categoryId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, q.categoryId);
            }
            ps.setString(3, q.title);
            ps.setString(4, q.content);
            ps.setString(5, q.type == null ? "essay" : q.type);
            ps.setString(6, q.options);
            ps.setString(7, q.correctAnswer);
            ps.setString(8, q.answer);
            ps.setString(9, q.analysis);
            ps.setString(10, q.source);
            ps.setInt(11, q.difficulty == null ? 2 : q.difficulty);
            ps.setLong(12, q.id);
            ps.setLong(13, userId);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新题目失败: " + e.getMessage(), e);
        }
    }

    public int updateAnalysis(long id, long userId, String analysis) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE t_question SET analysis = ? WHERE id = ? AND user_id = ? AND status = 1")) {
            ps.setString(1, analysis);
            ps.setLong(2, id);
            ps.setLong(3, userId);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新解析失败: " + e.getMessage(), e);
        }
    }

    /** 高频知识点 TOP N（按错题数倒序） */
    public List<Map<String, Object>> countGroupByCategory(long userId, int limit) {
        String sql = "SELECT c.name AS name, COUNT(q.id) AS cnt FROM t_question q "
                + "JOIN t_category c ON c.id = q.category_id "
                + "WHERE q.user_id = ? AND q.status = 1 "
                + "GROUP BY c.id, c.name ORDER BY cnt DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString("name"));
                    m.put("count", rs.getLong("cnt"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("知识点统计失败: " + e.getMessage(), e);
        }
        return list;
    }

    public long countByUser(long userId) {
        return scalarLong("SELECT COUNT(*) FROM t_question WHERE user_id = ? AND status = 1", userId);
    }

    public long countToday(long userId) {
        String sql = "SELECT COUNT(*) FROM t_question WHERE user_id = ? AND status = 1 AND DATE(created_at) = CURDATE()";
        return scalarLong(sql, userId);
    }

    public long countUploads(long userId) {
        return scalarLong("SELECT COUNT(*) FROM t_upload WHERE user_id = ?", userId);
    }

    public List<Map<String, Object>> countGroupBySubject(long userId) {
        String sql = "SELECT s.name AS name, COUNT(q.id) AS cnt FROM t_subject s "
                + "LEFT JOIN t_question q ON q.subject_id = s.id AND q.user_id = ? AND q.status = 1 "
                + "GROUP BY s.id, s.name, s.sort_no ORDER BY s.sort_no";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString("name"));
                    m.put("count", rs.getLong("cnt"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("学科统计失败: " + e.getMessage(), e);
        }
        return list;
    }

    /** 难度分布：1 简单 / 2 中等 / 3 困难 */
    public List<Map<String, Object>> countGroupByDifficulty(long userId) {
        String sql = "SELECT q.difficulty AS d, COUNT(*) AS cnt FROM t_question q "
                + "WHERE q.user_id = ? AND q.status = 1 "
                + "GROUP BY q.difficulty ORDER BY q.difficulty";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int d = rs.getInt("d");
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", d == 1 ? "简单" : d == 3 ? "困难" : "中等");
                    m.put("count", rs.getLong("cnt"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("难度统计失败: " + e.getMessage(), e);
        }
        return list;
    }

    /** 近 6 个月每月录入量，用于总结页趋势 */
    public List<Map<String, Object>> countGroupByMonth(long userId) {
        String sql = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS m, COUNT(*) AS cnt FROM t_question "
                + "WHERE user_id = ? AND status = 1 AND created_at >= DATE_SUB(NOW(), INTERVAL 6 MONTH) "
                + "GROUP BY m ORDER BY m";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", rs.getString("m"));
                    m.put("count", rs.getLong("cnt"));
                    list.add(m);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("月度统计失败: " + e.getMessage(), e);
        }
        return list;
    }

    private long scalarLong(String sql, long userId) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("统计失败: " + e.getMessage(), e);
        }
    }

    private Question map(ResultSet rs) throws Exception {
        Question q = new Question();
        q.id = rs.getLong("id");
        q.userId = rs.getLong("user_id");
        q.subjectId = rs.getInt("subject_id");
        q.subjectName = rs.getString("subject_name");
        int cid = rs.getInt("category_id");
        q.categoryId = rs.wasNull() ? null : cid;
        q.categoryName = rs.getString("category_name");
        q.title = rs.getString("title");
        q.content = rs.getString("content");
        q.type = rs.getString("q_type");
        q.options = rs.getString("options");
        q.correctAnswer = rs.getString("correct_answer");
        q.imagePath = rs.getString("image_path");
        q.answer = rs.getString("answer");
        q.analysis = rs.getString("analysis");
        q.source = rs.getString("source");
        q.difficulty = rs.getInt("difficulty");
        q.status = rs.getInt("status");
        Timestamp created = rs.getTimestamp("created_at");
        q.createdAt = created == null ? null : new java.util.Date(created.getTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        q.updatedAt = updated == null ? null : new java.util.Date(updated.getTime());
        return q;
    }
}
