package com.aistudy.dao;

import com.aistudy.model.Category;
import com.aistudy.model.Subject;
import com.aistudy.util.DbUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CatalogDao {

    public List<Subject> listSubjects() {
        String sql = "SELECT s.id, s.name, s.code, s.sort_no, "
                + "(SELECT COUNT(*) FROM t_question q WHERE q.subject_id = s.id AND q.status = 1) AS cnt, "
                + "(SELECT COUNT(*) FROM t_category c WHERE c.subject_id = s.id) AS cat_cnt "
                + "FROM t_subject s ORDER BY s.sort_no, s.id";
        List<Subject> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Subject s = new Subject();
                s.id = rs.getInt("id");
                s.name = rs.getString("name");
                s.code = rs.getString("code");
                s.sortNo = rs.getInt("sort_no");
                s.questionCount = rs.getLong("cnt");
                s.categoryCount = rs.getLong("cat_cnt");
                list.add(s);
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException("查询学科失败: " + e.getMessage(), e);
        }
    }

    public List<Category> listCategories(Integer subjectId, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.subject_id, c.parent_id, c.name, c.description, c.sort_no, s.name AS subject_name, "
                        + "(SELECT COUNT(*) FROM t_question q WHERE q.category_id = c.id AND q.status = 1) AS cnt "
                        + "FROM t_category c JOIN t_subject s ON s.id = c.subject_id WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (subjectId != null) {
            sql.append("AND c.subject_id = ? ");
            args.add(subjectId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND c.name LIKE ? ");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append("ORDER BY s.sort_no, c.sort_no, c.id");

        List<Category> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException("查询分类失败: " + e.getMessage(), e);
        }
    }

    public long insertCategory(int subjectId, int parentId, String name, String description) {
        String sql = "INSERT INTO t_category(subject_id, parent_id, name, description, sort_no) VALUES(?,?,?,?,?)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, subjectId);
            ps.setInt(2, parentId);
            ps.setString(3, name);
            ps.setString(4, description);
            ps.setInt(5, 99);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("新增分类失败: " + e.getMessage(), e);
        }
    }

    public boolean categoryExists(int subjectId, String name) {
        String sql = "SELECT 1 FROM t_category WHERE subject_id = ? AND name = ? LIMIT 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("校验分类失败: " + e.getMessage(), e);
        }
    }

    public boolean categoryExistsExcept(int subjectId, String name, int excludeId) {
        String sql = "SELECT 1 FROM t_category WHERE subject_id = ? AND name = ? AND id <> ? LIMIT 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            ps.setString(2, name);
            ps.setInt(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("校验分类失败: " + e.getMessage(), e);
        }
    }

    public Category findCategoryById(int id) {
        String sql = "SELECT c.id, c.subject_id, c.parent_id, c.name, c.description, c.sort_no, s.name AS subject_name, "
                + "(SELECT COUNT(*) FROM t_question q WHERE q.category_id = c.id AND q.status = 1) AS cnt "
                + "FROM t_category c JOIN t_subject s ON s.id = c.subject_id WHERE c.id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (Exception e) {
            throw new RuntimeException("查询分类失败: " + e.getMessage(), e);
        }
    }

    public int updateCategory(int id, String name, String description, Integer parentId) {
        StringBuilder sql = new StringBuilder("UPDATE t_category SET name = ?, description = ?");
        if (parentId != null) {
            sql.append(", parent_id = ?");
        }
        sql.append(" WHERE id = ?");
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, name);
            ps.setString(2, description);
            int i = 3;
            if (parentId != null) {
                ps.setInt(i++, parentId);
            }
            ps.setInt(i, id);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("更新分类失败: " + e.getMessage(), e);
        }
    }

    public int deleteCategory(int id) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM t_category WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除分类失败: " + e.getMessage(), e);
        }
    }

    public boolean subjectExists(int subjectId) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM t_subject WHERE id = ?")) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("校验学科失败: " + e.getMessage(), e);
        }
    }

    /** 按名称查学科（忽略大小写与首尾空格） */
    public Subject findSubjectByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT id, name, code, sort_no FROM t_subject WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Subject s = new Subject();
                s.id = rs.getInt("id");
                s.name = rs.getString("name");
                s.code = rs.getString("code");
                s.sortNo = rs.getInt("sort_no");
                return s;
            }
        } catch (Exception e) {
            throw new RuntimeException("查询学科失败: " + e.getMessage(), e);
        }
    }

    public int insertSubject(String name, String code, int sortNo) {
        String sql = "INSERT INTO t_subject(name, code, sort_no) VALUES(?,?,?)";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, code);
            ps.setInt(3, sortNo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new RuntimeException("新增学科失败: " + e.getMessage(), e);
        }
    }

    public int updateSubjectName(int id, String name) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE t_subject SET name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, id);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("重命名学科失败: " + e.getMessage(), e);
        }
    }

    public long countCategoriesBySubject(int subjectId) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM t_category WHERE subject_id = ?")) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("统计分类失败: " + e.getMessage(), e);
        }
    }

    public long countQuestionsBySubject(int subjectId) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM t_question WHERE subject_id = ? AND status = 1")) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception e) {
            throw new RuntimeException("统计题目失败: " + e.getMessage(), e);
        }
    }

    public int deleteSubject(int id) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM t_subject WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除学科失败: " + e.getMessage(), e);
        }
    }

    /** 学科编码：内置学科用英文缩写，自建学科用 U + 名称哈希，保证唯一且可追踪 */
    public String buildSubjectCode(String name) {
        String base = "U" + Integer.toHexString(name.trim().hashCode()).toUpperCase();
        String code = base.length() > 28 ? base.substring(0, 28) : base;
        int seq = 1;
        while (subjectCodeExists(code)) {
            code = base + "_" + seq;
            seq++;
        }
        return code;
    }

    public boolean subjectCodeExists(String code) {
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM t_subject WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("校验学科编码失败: " + e.getMessage(), e);
        }
    }

    private Category map(ResultSet rs) throws Exception {
        Category c = new Category();
        c.id = rs.getInt("id");
        c.subjectId = rs.getInt("subject_id");
        c.parentId = rs.getInt("parent_id");
        c.name = rs.getString("name");
        c.description = rs.getString("description");
        c.sortNo = rs.getInt("sort_no");
        c.subjectName = rs.getString("subject_name");
        c.questionCount = rs.getLong("cnt");
        return c;
    }
}
