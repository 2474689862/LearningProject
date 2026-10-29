package com.aistudy.action;

import com.aistudy.dao.UploadDao;
import com.aistudy.model.User;
import com.aistudy.util.UploadPaths;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 题目图片上传：保存到独立的上传目录（{@link UploadPaths}），返回可访问 URL。
 *
 * <p>图片不再放进 webapp，避免 mvn clean / 重新部署时被清空；
 * 访问由 {@code UploadServlet} 映射到 {@code /uploads/*}。</p>
 *
 * <p>Struts 7 的默认上传拦截器改为 ActionFileUploadInterceptor，
 * 不再向 Action 注入 image / imageFileName / imageContentType 三个属性，
 * 而是回调 {@link UploadedFilesAware#withUploadedFiles(List)}。</p>
 */
public class UploadAction extends BaseAction implements UploadedFilesAware {

    private static final long MAX_SIZE = 10 * 1024 * 1024L;
    private static final List<String> ALLOWED_EXT = Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");

    private final UploadDao uploadDao = new UploadDao();

    /** 表单字段名，与前端 FormData 的 key 保持一致 */
    private static final String FIELD = "image";

    private List<UploadedFile> uploadedFiles = Collections.emptyList();

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        this.uploadedFiles = uploadedFiles == null ? Collections.emptyList() : uploadedFiles;
    }

    public String image() {
        try {
            User user = currentUser();
            if (user == null) {
                return fail("请先登录");
            }
            UploadedFile image = pick(FIELD);
            if (image == null) {
                return fail("请选择要上传的图片");
            }
            String originalName = image.getOriginalName() == null ? "" : image.getOriginalName();
            long size = image.length() == null ? 0L : image.length();
            String contentType = image.getContentType();
            if (size <= 0) {
                return fail("上传的文件为空");
            }
            if (size > MAX_SIZE) {
                return fail("图片不能超过 10MB");
            }
            String ext = extension(originalName);
            if (!ALLOWED_EXT.contains(ext)) {
                return fail("仅支持 jpg / jpeg / png / gif / bmp / webp 格式");
            }
            if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return fail("文件类型不是图片");
            }

            String monthDir = new SimpleDateFormat("yyyyMM").format(new Date());
            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            String relative = UploadPaths.url(monthDir, fileName);

            // 存到 Web 应用之外，mvn clean / 重新部署都不会丢图
            Path dir = UploadPaths.root().resolve(monthDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            try (InputStream in = image.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            uploadDao.insert(user.id, originalName, relative, contentType, size);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", relative);
            data.put("fileName", fileName);
            data.put("originalName", originalName);
            data.put("size", size);
            return ok("上传成功", data);
        } catch (Exception e) {
            return fail("上传失败：" + e.getMessage());
        }
    }

    /** 取指定表单字段上传的文件，忽略空文件域 */
    private UploadedFile pick(String field) {
        for (UploadedFile f : uploadedFiles) {
            if (field.equals(f.getInputName()) && !f.isMissing()) {
                return f;
            }
        }
        return null;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
