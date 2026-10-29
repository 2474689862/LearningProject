package com.aistudy.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传图片的存储位置。
 *
 * <p>图片不再存进 webapp 目录（那里属于构建产物，mvn clean 或重新部署会被清空），
 * 改为独立目录。解析优先级：</p>
 * <ol>
 *   <li>JVM 参数 {@code -Dapp.uploads.dir=...}</li>
 *   <li>环境变量 {@code APP_UPLOADS_DIR}</li>
 *   <li>默认 {@code ${user.home}/ai-study-uploads}</li>
 * </ol>
 */
public final class UploadPaths {

    private static final String DEFAULT_DIR_NAME = "ai-study-uploads";

    private UploadPaths() {
    }

    /** 图片根目录（绝对路径） */
    public static Path root() {
        String configured = System.getProperty("app.uploads.dir");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("APP_UPLOADS_DIR");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured.trim()).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.home", "."), DEFAULT_DIR_NAME).toAbsolutePath();
    }

    /** 对外访问路径，同时作为数据库里保存的相对地址 */
    public static String url(String yearMonth, String fileName) {
        return "/uploads/" + yearMonth + "/" + fileName;
    }

    /**
     * 把数据库里的 {@code /uploads/yyyyMM/xxx.jpg} 还原成本地文件。
     * 路径不合法（不以 /uploads/ 开头或含 ..）时返回 null。
     */
    public static Path resolve(String urlPath) {
        if (urlPath == null || !urlPath.startsWith("/uploads/") || urlPath.contains("..")) {
            return null;
        }
        return root().resolve(urlPath.substring("/uploads/".length()));
    }
}
