package com.aistudy.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 数据库连接工具：读取 db.properties，支持环境变量覆盖。
 * 没有 db.properties 时使用本机默认值（127.0.0.1:3306 / ai_study / root / 空密码）。
 */
public final class DbUtil {

    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/ai_study"
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
            + "&allowPublicKeyRetrieval=true&useSSL=false&allowMultiQueries=true";

    private static String url;
    private static String username;
    private static String password;

    static {
        Properties props = new Properties();
        try (InputStream in = DbUtil.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError("加载 db.properties 失败: " + e.getMessage());
        }
        try {
            Class.forName(props.getProperty("jdbc.driver", "com.mysql.cj.jdbc.Driver"));
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL 驱动未找到");
        }
        url = env("DB_URL", props.getProperty("jdbc.url", DEFAULT_URL));
        username = env("DB_USER", props.getProperty("jdbc.username", "root"));
        password = env("DB_PASSWORD", props.getProperty("jdbc.password", ""));
    }

    private DbUtil() {
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.trim().isEmpty()) {
            v = System.getProperty(key);
        }
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    public static String getUrl() {
        return url;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /** 不带库名的连接（用于建库） */
    public static Connection getServerConnection() throws SQLException {
        return DriverManager.getConnection(serverUrl(), username, password);
    }

    public static String databaseName() {
        String u = url;
        int q = u.indexOf('?');
        if (q > 0) {
            u = u.substring(0, q);
        }
        int slash = u.lastIndexOf('/');
        return slash > 0 ? u.substring(slash + 1) : "ai_study";
    }

    private static String serverUrl() {
        String u = url;
        int q = u.indexOf('?');
        String params = q > 0 ? u.substring(q) : "";
        String base = q > 0 ? u.substring(0, q) : u;
        int slash = base.lastIndexOf('/');
        String root = slash > 0 ? base.substring(0, slash + 1) : base;
        return root + params;
    }
}
