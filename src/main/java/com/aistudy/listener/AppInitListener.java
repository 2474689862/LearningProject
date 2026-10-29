package com.aistudy.listener;

import com.aistudy.config.AppSettings;
import com.aistudy.dao.UserDao;
import com.aistudy.model.User;
import com.aistudy.util.DbUtil;
import com.aistudy.util.PasswordUtil;
import com.aistudy.util.UploadPaths;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 应用启动初始化：建库建表、灌入学科分类、创建演示账号。
 * 数据库不可用时不阻断启动，仅打印日志。
 */
public class AppInitListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ensureDatabase();
            runScript("db/schema.sql");
            // t_setting 建好后加载页面设置，供 AiConfig 优先读取
            AppSettings.reload();
            ensureQuestionColumns();
            // 幂等脚本：新库补齐九门学科与常见题型，已有库只补缺失项，不会动用户数据
            mergeDuplicateSubjects();
            runScript("db/seed.sql");
            createDemoUsers();
            ensureUploadDir(sce);
            System.out.println("[AI-Study] 数据库初始化完成，演示账号：student / 123456");
        } catch (Exception e) {
            System.err.println("[AI-Study] 数据库初始化失败，请检查 db.properties：" + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // nothing
    }

    private void ensureDatabase() throws Exception {
        String db = DbUtil.databaseName();
        try (Connection conn = DbUtil.getServerConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + db.replace("`", "")
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
    }

    private void runScript(String resource) throws Exception {
        String script = readResource(resource);
        if (script == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : script.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : sb.toString().split(";")) {
            if (!part.trim().isEmpty()) {
                statements.add(part.trim());
            }
        }
        try (Connection conn = DbUtil.getConnection();
             Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    /**
     * 老库兼容：schema.sql 用的是 CREATE TABLE IF NOT EXISTS，已存在的 t_question 不会自动加新列。
     * 这里按 information_schema 检查后逐列补齐（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。
     */
    private void ensureQuestionColumns() throws Exception {
        Map<String, String> want = new LinkedHashMap<>();
        want.put("q_type", "VARCHAR(20) NOT NULL DEFAULT 'essay' COMMENT '题型：choice/judge/blank/short/essay'");
        want.put("options", "TEXT NULL COMMENT '选择题选项（JSON 数组）'");
        want.put("correct_answer", "TEXT NULL COMMENT '标准答案'");

        List<String> exist = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = ? AND table_name = 't_question'")) {
            ps.setString(1, DbUtil.databaseName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    exist.add(rs.getString(1).toLowerCase(Locale.ROOT));
                }
            }
            if (exist.isEmpty()) {
                return;
            }
            for (Map.Entry<String, String> col : want.entrySet()) {
                if (exist.contains(col.getKey())) {
                    continue;
                }
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE t_question ADD COLUMN `" + col.getKey() + "` " + col.getValue());
                    System.out.println("[AI-Study] t_question 补充字段：" + col.getKey());
                }
            }
            // 老题目没有题型，统一标为解答题，编辑时按通用模板展示
            try (Statement st = conn.createStatement()) {
                int rows = st.executeUpdate("UPDATE t_question SET q_type = 'essay' WHERE q_type IS NULL OR q_type = ''");
                if (rows > 0) {
                    System.out.println("[AI-Study] 老题目补齐题型标记：" + rows + " 条");
                }
            }
        }
    }

    /**
     * AI 自动归档时会按随机编码新建学科，可能与种子数据里的同名学科重复。
     * 同名学科只保留「被题目引用最多、id 最小」的一条，其余的分类和题目
     * 改挂到保留项后删除，保证前端不会出现两个同名学科。
     */
    private void mergeDuplicateSubjects() throws Exception {
        try (Connection conn = DbUtil.getConnection()) {
            List<String> names = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM t_subject GROUP BY name HAVING COUNT(*) > 1");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
            for (String name : names) {
                List<Integer> ids = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT s.id, (SELECT COUNT(*) FROM t_question q WHERE q.subject_id = s.id) AS cnt "
                                + "FROM t_subject s WHERE s.name = ? ORDER BY cnt DESC, s.id ASC")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ids.add(rs.getInt(1));
                        }
                    }
                }
                if (ids.size() < 2) {
                    continue;
                }
                int keep = ids.get(0);
                for (int drop : ids.subList(1, ids.size())) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE t_category SET subject_id = ? WHERE subject_id = ?")) {
                        ps.setInt(1, keep);
                        ps.setInt(2, drop);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE t_question SET subject_id = ? WHERE subject_id = ?")) {
                        ps.setInt(1, keep);
                        ps.setInt(2, drop);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM t_subject WHERE id = ?")) {
                        ps.setInt(1, drop);
                        ps.executeUpdate();
                    }
                }
                System.out.println("[AI-Study] 合并同名学科：" + name + "，保留 id=" + keep);
            }
        }
    }

    private String readResource(String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void createDemoUsers() throws Exception {
        UserDao userDao = new UserDao();
        if (userDao.findByUsername("student") == null) {
            User u = new User();
            u.username = "student";
            u.salt = PasswordUtil.DEMO_SALT;
            u.password = PasswordUtil.hash("123456", u.salt);
            u.nickname = "演示同学";
            u.role = "STUDENT";
            userDao.insert(u);
        }
        if (userDao.findByUsername("admin") == null) {
            User u = new User();
            u.username = "admin";
            u.salt = PasswordUtil.DEMO_SALT;
            u.password = PasswordUtil.hash("admin123", u.salt);
            u.nickname = "管理员";
            u.role = "ADMIN";
            userDao.insert(u);
        }
    }

    private void ensureUploadDir(ServletContextEvent sce) {
        try {
            Files.createDirectories(UploadPaths.root());
        } catch (Exception e) {
            System.err.println("[AI-Study] 创建上传目录失败：" + e.getMessage());
        }
    }
}
