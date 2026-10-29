package com.aistudy.web;

import com.aistudy.util.UploadPaths;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 读取上传目录里的图片：{@code GET /uploads/yyyyMM/xxx.jpg}。
 *
 * <p>图片存放在 Web 应用之外，Tomcat 无法直接当静态资源返回，因此由本 Servlet 代理。
 * 只允许 GET/HEAD，路径必须是「年月目录 + 文件名」且不含 {@code ..}，
 * 解析后仍要落在根目录内，防止目录穿越。</p>
 */
public class UploadServlet extends HttpServlet {

    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9_-]{1,32}/[A-Za-z0-9._-]{1,128}$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String info = req.getPathInfo();
        if (info == null || !SAFE_PATH.matcher(info).matches()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Path root = UploadPaths.root();
        Path file = root.resolve(info.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String name = file.getFileName().toString();
        String type = getServletContext().getMimeType(name);
        resp.setContentType(type == null ? "application/octet-stream" : type);
        resp.setContentLengthLong(Files.size(file));
        resp.setHeader("Cache-Control", "public, max-age=86400");
        resp.setDateHeader("Last-Modified", Files.getLastModifiedTime(file).toMillis());
        Files.copy(file, resp.getOutputStream());
    }
}
