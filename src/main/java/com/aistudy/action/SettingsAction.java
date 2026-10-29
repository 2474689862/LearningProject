package com.aistudy.action;

import com.aistudy.ai.AiConfig;
import com.aistudy.ai.AiService;
import com.aistudy.config.AppSettings;
import com.aistudy.dao.AiDao;
import com.aistudy.model.User;
import com.aistudy.util.DbUtil;
import com.aistudy.util.UploadPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 设置页接口：读取 / 保存 / 测试连接 / 诊断 / 恢复默认。
 * <p>保存的是「全局」配置（t_setting），所有登录用户都能改，主要用于本地与云端对比；
 * 保存后立即生效（AiConfig 每次取值都会看覆盖层），不需要重启 Tomcat。
 * 浏览器侧的偏好（联网校验、默认学科、KaTeX 开关）不经过这里，直接存 localStorage。</p>
 */
public class SettingsAction extends BaseAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 允许页面写入的键，其它键一律拒绝，避免误改数据库/检索等核心配置 */
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "ai.provider", "ai.cloud.vendor", "ai.cloud.baseUrl", "ai.cloud.apiKey", "ai.cloud.model",
            "ai.ollama.baseUrl", "ai.ollama.textModel", "ai.ollama.visionModel",
            "ai.ollama.singleModel", "ai.ollama.keepAlive",
            "ai.timeout.seconds", "ai.max.tokens", "ai.temperature",
            "ai.ocr.twoStage", "ai.ocr.preprocess",
            "ai.vision.maxTokens", "ai.vision.numCtx", "ai.vision.temperature",
            "ai.search.enabled", "ai.search.topN",
            "ai.search.default.maxChars", "ai.search.default.timeoutMs",
            "ai.search.anysearch.maxChars", "ai.search.anysearch.timeoutMs",
            "ai.search.anysearch.baseUrl", "ai.search.cache.size", "ai.search.cache.ttlMinutes"));

    private static final Set<String> PROVIDERS = new HashSet<>(Arrays.asList("auto", "ollama", "cloud", "local"));

    private final AiService aiService = new AiService();
    private final AiDao aiDao = new AiDao();

    private String data;

    /** 读取当前配置：页面展示用，敏感项只回掩码 */
    public String get() {
        try {
            currentUser();
            Map<String, Object> out = new LinkedHashMap<>();
            String resolved = AiConfig.resolvedProvider();
            out.put("provider", AiConfig.configuredProvider());
            out.put("resolvedProvider", resolved);
            out.put("providerLabel", AiConfig.providerLabel(resolved));
            out.put("vendor", AiConfig.cloudVendor());
            out.put("vendorLabel", AiConfig.cloudVendorLabel());
            out.put("vendorLevel", AiConfig.cloudVendorLevel());
            out.put("vendors", AiConfig.cloudVendors());
            out.put("cloudBaseUrl", AiConfig.cloudBaseUrl());
            out.put("cloudModel", AiConfig.cloudModel());
            out.put("cloudApiKeyMasked", AppSettings.masked("ai.cloud.apiKey"));
            out.put("hasCloudKey", AiConfig.hasCloudKey());
            out.put("cloudKeySource", cloudKeySource());
            out.put("ollamaBaseUrl", AiConfig.ollamaBaseUrl());
            out.put("ollamaTextModel", AiConfig.ollamaTextModel());
            out.put("ollamaVisionModel", AiConfig.ollamaVisionModel());
            out.put("singleModel", AiConfig.singleModelOnly());
            out.put("keepAlive", AiConfig.keepAlive());
            out.put("timeoutSeconds", AiConfig.timeoutSeconds());
            out.put("maxTokens", AiConfig.maxTokens());
            out.put("temperature", AiConfig.temperature());
            out.put("twoStage", AiConfig.twoStageRecognize());
            out.put("preprocess", AiConfig.imagePreprocess());
            out.put("visionMaxTokens", AiConfig.visionMaxTokens());
            out.put("visionNumCtx", AiConfig.visionNumCtx());
            out.put("visionTemperature", AiConfig.visionTemperature());
            out.put("searchEnabled", AiConfig.searchEnabled());
            out.put("searchTopN", AiConfig.searchTopN());
            out.put("searchDefaultMaxChars", AiConfig.searchMaxChars("default"));
            out.put("searchDefaultTimeoutMs", AiConfig.searchTimeoutMs("default"));
            out.put("searchAnyMaxChars", AiConfig.searchMaxChars("anysearch"));
            out.put("searchAnyTimeoutMs", AiConfig.searchTimeoutMs("anysearch"));
            out.put("searchAnyBaseUrl", AiConfig.anySearchBase());
            out.put("searchCacheSize", AiConfig.searchCacheSize());
            out.put("searchCacheTtlMinutes", (int) (AiConfig.searchCacheTtlMs() / 60000L));
            out.put("overriddenKeys", new ArrayList<>(AppSettings.overriddenKeys()));
            out.put("ollamaReachable", AiConfig.reachable(AiConfig.ollamaBaseUrl()));
            out.put("textModelReady", AiService.modelReady("ollama", AiConfig.ollamaTextModel()));
            out.put("visionModelReady", AiService.modelReady("ollama", AiConfig.ollamaVisionModel()));
            return ok(out);
        } catch (Exception e) {
            return fail("读取设置失败：" + e.getMessage());
        }
    }

    /** 保存一组配置；值为空字符串表示删除覆盖项（回落到 ai.properties） */
    public String save() {
        try {
            User user = currentUser();
            if (isBlank(data)) {
                return fail("没有要保存的内容");
            }
            JsonNode root = MAPPER.readTree(data);
            if (root == null || !root.isObject()) {
                return fail("参数格式不正确");
            }
            Map<String, String> values = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                if (!ALLOWED.contains(key)) {
                    return fail("不支持的配置项：" + key);
                }
                JsonNode node = e.getValue();
                values.put(key, node == null || node.isNull() ? "" : node.asText("").trim());
            }
            String error = validate(values);
            if (error != null) {
                return fail(error);
            }
            AppSettings.save(values, user.id);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resolvedProvider", AiConfig.resolvedProvider());
            out.put("providerLabel", AiConfig.providerLabel(AiConfig.resolvedProvider()));
            out.put("overriddenKeys", new ArrayList<>(AppSettings.overriddenKeys()));
            return ok("已保存，立即生效", out);
        } catch (Exception e) {
            return fail("保存失败：" + e.getMessage());
        }
    }

    /** 测试当前生效的模型是否可用（跳过 60 秒缓存） */
    public String testAi() {
        try {
            currentUser();
            AiService.Health h = aiService.healthCheck(true);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", h.ok);
            out.put("busy", h.busy);
            out.put("provider", h.provider);
            out.put("providerLabel", AiConfig.providerLabel(h.provider));
            out.put("model", h.model);
            out.put("latencyMs", h.latencyMs);
            out.put("reason", h.reason == null ? "" : h.reason);
            out.put("visionModel", h.visionModel);
            out.put("visionReady", h.visionReady);
            out.put("vendor", AiConfig.cloudVendor());
            out.put("vendorLabel", AiConfig.cloudVendorLabel());
            out.put("vendorLevel", AiConfig.cloudVendorLevel());
            out.put("ollamaReachable", AiConfig.reachable(AiConfig.ollamaBaseUrl()));

            String hint;
            if ("local".equals(h.provider)) {
                hint = "当前没有可用模型，所有 AI 功能会退回本地规则模板。";
            } else if ("cloud".equals(h.provider)) {
                hint = h.visionReady
                        ? "文字任务走云端；图片识别仍走本机 Ollama 视觉模型 " + h.visionModel + "。"
                        : "文字任务走云端；识图模型 " + h.visionModel + " 未就绪，上传图片识别会失败。";
            } else {
                hint = "文字与识图都走本机 Ollama，同一时刻只加载一个模型。";
            }
            out.put("hint", hint);
            return ok(out);
        } catch (Exception e) {
            return fail("测试失败：" + e.getMessage());
        }
    }

    /** 系统诊断：只读信息，帮助排查「为什么 AI 不工作」 */
    public String diagnose() {
        try {
            User user = currentUser();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dbUrl", DbUtil.getUrl());
            out.put("dbName", DbUtil.databaseName());
            out.put("uploadsPath", UploadPaths.root().toString());
            out.put("provider", AiConfig.configuredProvider());
            out.put("resolvedProvider", AiConfig.resolvedProvider());
            out.put("providerLabel", AiConfig.providerLabel(AiConfig.resolvedProvider()));
            out.put("model", AiConfig.modelName(AiConfig.resolvedProvider()));
            out.put("visionModel", AiConfig.visionModelName(AiConfig.resolvedProvider()));
            out.put("cloudVendor", AiConfig.cloudVendorLabel());
            out.put("cloudKeyConfigured", AiConfig.hasCloudKey());
            out.put("cloudKeySource", cloudKeySource());
            out.put("ollamaReachable", AiConfig.reachable(AiConfig.ollamaBaseUrl()));
            out.put("overridden", AppSettings.snapshot());
            out.put("recentLogs", aiDao.recent(user.id, 5));
            return ok(out);
        } catch (Exception e) {
            return fail("诊断失败：" + e.getMessage());
        }
    }

    /** 清空页面设置，全部回落到 ai.properties */
    public String reset() {
        try {
            currentUser();
            AppSettings.reset();
            return ok("已恢复默认配置（回落到 ai.properties）", null);
        } catch (Exception e) {
            return fail("恢复默认失败：" + e.getMessage());
        }
    }

    // ---------------- 内部方法 ----------------

    private String cloudKeySource() {
        if (AppSettings.get("ai.cloud.apiKey") != null) {
            return "settings";
        }
        return AiConfig.hasCloudKey() ? "properties" : "";
    }

    /** 返回第一个错误信息；全部合法返回 null */
    private String validate(Map<String, String> v) {
        String provider = v.get("ai.provider");
        if (provider != null && !provider.isEmpty() && !PROVIDERS.contains(provider.toLowerCase(Locale.ROOT))) {
            return "运行模式不合法";
        }
        String vendor = v.get("ai.cloud.vendor");
        if (vendor != null && !vendor.isEmpty()) {
            boolean known = false;
            for (Map<String, String> item : AiConfig.cloudVendors()) {
                if (item.get("id").equals(vendor)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                return "未知的云端厂商";
            }
        }
        for (String key : new String[]{"ai.cloud.baseUrl", "ai.ollama.baseUrl"}) {
            String url = v.get(key);
            if (url != null && !url.isEmpty()
                    && !url.startsWith("http://") && !url.startsWith("https://")) {
                return (key.contains("cloud") ? "云端" : "Ollama") + " Base URL 必须以 http:// 或 https:// 开头";
            }
        }
        String err = checkInt(v, "ai.timeout.seconds", 5, 600, "超时时间");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.max.tokens", 64, 32000, "最大输出 token");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.vision.maxTokens", 64, 32000, "识图最大输出 token");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.vision.numCtx", 512, 131072, "识图上下文长度");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.search.topN", 1, 10, "检索保留条数");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.search.default.maxChars", 200, 10000, "默认档证据字数");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.search.default.timeoutMs", 500, 30000, "默认档超时");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.search.anysearch.maxChars", 200, 10000, "AnySearch 档证据字数");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.search.anysearch.timeoutMs", 500, 30000, "AnySearch 档超时");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.search.cache.size", 0, 500, "结果缓存条数");
        if (err != null) {
            return err;
        }
        err = checkInt(v, "ai.search.cache.ttlMinutes", 1, 10080, "缓存有效期");
        if (err != null) {
            return err;
        }
        String anyBase = v.get("ai.search.anysearch.baseUrl");
        if (anyBase != null && !anyBase.isEmpty()
                && !anyBase.startsWith("http://") && !anyBase.startsWith("https://")) {
            return "AnySearch 接口地址必须以 http:// 或 https:// 开头";
        }
        err = checkDouble(v, "ai.temperature", 0, 2, "温度");
        if (err != null) {
            return err;
        }
        err = checkDouble(v, "ai.vision.temperature", 0, 2, "识图温度");
        if (err != null) {
            return err;
        }
        for (String key : new String[]{"ai.ollama.singleModel", "ai.ocr.twoStage",
                "ai.ocr.preprocess", "ai.search.enabled"}) {
            String b = v.get(key);
            if (b != null && !b.isEmpty()
                    && !"true".equalsIgnoreCase(b) && !"false".equalsIgnoreCase(b)) {
                return "开关类配置只能是 true 或 false";
            }
        }
        return null;
    }

    private String checkInt(Map<String, String> v, String key, int min, int max, String label) {
        String s = v.get(key);
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            int n = Integer.parseInt(s);
            if (n < min || n > max) {
                return label + "需在 " + min + " - " + max + " 之间";
            }
        } catch (Exception e) {
            return label + "必须是整数";
        }
        return null;
    }

    private String checkDouble(Map<String, String> v, String key, double min, double max, String label) {
        String s = v.get(key);
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            double n = Double.parseDouble(s);
            if (n < min || n > max) {
                return label + "需在 " + min + " - " + max + " 之间";
            }
        } catch (Exception e) {
            return label + "必须是数字";
        }
        return null;
    }

    public void setData(String data) {
        this.data = data;
    }
}
