package com.aistudy.ai;

import com.aistudy.config.AppSettings;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * AI 配置读取与提供方解析。
 * <p>优先级：环境变量 &gt; 设置页写入的 t_setting &gt; ai.properties &gt; 代码默认值。
 * provider=auto 时按「云端 Key → 本机 Ollama → 本地规则」的顺序自动选择。</p>
 * <p>识图（视觉）固定走本机 Ollama：当前云端厂商都没有开放通用的公网视觉接口，
 * 强行把图片发给文字模型只会失败，所以 {@link #visionModelName(String)} 始终返回本地视觉模型。</p>
 */
public final class AiConfig {

    private static final Properties PROPS = load();

    private AiConfig() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AiConfig.class.getClassLoader().getResourceAsStream("ai.properties")) {
            if (in != null) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 读取失败时使用默认值
        }
        return p;
    }

    private static String value(String key, String envKey, String defaultValue) {
        String env = envKey == null ? null : System.getenv(envKey);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String stored = AppSettings.get(key);
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }
        String v = PROPS.getProperty(key);
        return (v == null || v.trim().isEmpty()) ? defaultValue : v.trim();
    }

    /** 只读配置文件原始值，用于兼容旧版 ai.deepseek.* 配置 */
    private static String legacy(String key) {
        String v = PROPS.getProperty(key);
        return v == null ? "" : v.trim();
    }

    // ---------------- 提供方 ----------

    public static String configuredProvider() {
        return value("ai.provider", "AI_PROVIDER", "auto").toLowerCase(Locale.ROOT);
    }

    /**
     * 实际生效的提供方：cloud / ollama / local。
     * 兼容旧值 deepseek（等价于 cloud）与 auto。
     */
    public static String resolvedProvider() {
        String p = configuredProvider();
        if ("cloud".equals(p) || "deepseek".equals(p)) {
            return hasCloudKey() ? "cloud" : "local";
        }
        if ("ollama".equals(p)) {
            return "ollama";
        }
        if ("local".equals(p)) {
            return "local";
        }
        if (hasCloudKey()) {
            return "cloud";
        }
        return reachable(ollamaBaseUrl()) ? "ollama" : "local";
    }

    public static String providerLabel(String provider) {
        if ("cloud".equals(provider) || "deepseek".equals(provider)) {
            return "云端 · " + cloudVendorLabel();
        }
        if ("ollama".equals(provider)) {
            return "本机 Ollama";
        }
        return "本地规则模板";
    }

    // ---------------- 云端（OpenAI 兼容）----------------

    public static String cloudVendor() {
        return value("ai.cloud.vendor", "AI_CLOUD_VENDOR", "deepseek").toLowerCase(Locale.ROOT);
    }

    /** 各厂商默认 Base URL，设置页切换厂商时自动带出 */
    public static String cloudVendorBaseUrl(String vendor) {
        switch (vendor == null ? "" : vendor) {
            case "openai":
                return "https://api.openai.com/v1";
            case "moonshot":
                return "https://api.moonshot.cn/v1";
            case "zhipu":
                return "https://open.bigmodel.cn/api/paas/v4";
            case "qwen":
                return "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "siliconflow":
                return "https://api.siliconflow.cn/v1";
            case "anthropic":
                return "https://api.anthropic.com/v1";
            case "gemini":
                return "https://generativelanguage.googleapis.com/v1beta/openai";
            case "bailian":
                return "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "azure":
                // Azure 的地址与资源名绑定，无法给通用默认值，必须由用户填写
                return "";
            default:
                return "https://api.deepseek.com/v1";
        }
    }

    /** 各厂商默认模型名 */
    public static String cloudVendorModel(String vendor) {
        switch (vendor == null ? "" : vendor) {
            case "openai":
                return "gpt-4o-mini";
            case "moonshot":
                return "moonshot-v1-8k";
            case "zhipu":
                return "glm-4-flash";
            case "qwen":
                return "qwen-plus";
            case "siliconflow":
                return "deepseek-ai/DeepSeek-V3";
            case "anthropic":
                return "claude-3-5-sonnet-latest";
            case "gemini":
                return "gemini-2.0-flash";
            case "azure":
                return "gpt-4o-mini";
            default:
                return "deepseek-flash";
        }
    }

    public static String cloudVendorLabel() {
        switch (cloudVendor()) {
            case "openai":
                return "OpenAI";
            case "moonshot":
                return "Moonshot Kimi";
            case "zhipu":
                return "智谱 GLM";
            case "qwen":
                return "通义千问（兼容模式）";
            case "siliconflow":
                return "硅基流动";
            case "anthropic":
                return "Anthropic Claude";
            case "gemini":
                return "Google Gemini";
            case "azure":
                return "Azure OpenAI";
            case "bailian":
                return "阿里百炼";
            case "custom":
                return "自定义网关";
            default:
                return "DeepSeek";
        }
    }

    /** 适配等级：best 完整适配 / limited 可选但功能受限 / unsupported 未适配 */
    public static String cloudVendorLevel() {
        switch (cloudVendor()) {
            case "deepseek":
            case "custom":
                return "best";
            case "openai":
            case "moonshot":
            case "zhipu":
            case "qwen":
            case "siliconflow":
                return "limited";
            default:
                return "unsupported";
        }
    }

    /** 设置页厂商下拉用：id / 名称 / 默认地址 / 默认模型 / 适配等级 */
    public static List<Map<String, String>> cloudVendors() {
        String[][] rows = {
                {"deepseek", "DeepSeek", "best"},
                {"openai", "OpenAI", "limited"},
                {"moonshot", "Moonshot Kimi", "limited"},
                {"zhipu", "智谱 GLM", "limited"},
                {"qwen", "通义千问（兼容模式）", "limited"},
                {"siliconflow", "硅基流动", "limited"},
                {"custom", "自定义 OpenAI 兼容网关", "best"},
                {"anthropic", "Anthropic Claude", "unsupported"},
                {"gemini", "Google Gemini", "unsupported"},
                {"azure", "Azure OpenAI", "unsupported"},
                {"bailian", "阿里百炼", "unsupported"}
        };
        List<Map<String, String>> list = new ArrayList<>();
        for (String[] row : rows) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", row[0]);
            item.put("name", row[1]);
            item.put("level", row[2]);
            item.put("baseUrl", cloudVendorBaseUrl(row[0]));
            item.put("model", cloudVendorModel(row[0]));
            list.add(item);
        }
        return list;
    }

    public static String cloudBaseUrl() {
        return trimSlash(value("ai.cloud.baseUrl", "AI_CLOUD_BASE_URL",
                cloudVendorBaseUrl(cloudVendor())));
    }

    public static String cloudApiKey() {
        String key = value("ai.cloud.apiKey", "AI_CLOUD_API_KEY", "");
        if (key.isEmpty()) {
            // 兼容旧配置：只写了 ai.deepseek.apiKey 的环境
            key = legacy("ai.deepseek.apiKey");
        }
        return key;
    }

    public static String cloudModel() {
        return value("ai.cloud.model", "AI_CLOUD_MODEL", cloudVendorModel(cloudVendor()));
    }

    public static boolean hasCloudKey() {
        return !cloudApiKey().isEmpty();
    }

    // ---------------- 本机 Ollama ----------------

    public static String ollamaBaseUrl() {
        return trimSlash(value("ai.ollama.baseUrl", "OLLAMA_BASE_URL", "http://127.0.0.1:11434/v1"));
    }

    public static String ollamaModel() {
        return value("ai.ollama.model", "OLLAMA_MODEL", "qwen3:8b");
    }

    /** 文字任务模型（解析、问答、总结、分类），默认取主模型 */
    public static String ollamaTextModel() {
        return value("ai.ollama.textModel", "OLLAMA_TEXT_MODEL", ollamaModel());
    }

    /** 图片识别模型（需要多模态能力，如 qwen2.5vl） */
    public static String ollamaVisionModel() {
        return value("ai.ollama.visionModel", "OLLAMA_VISION_MODEL", ollamaModel());
    }

    /** 是否保证同一时刻只加载一个 Ollama 模型（文字/识图切换时自动卸载另一个） */
    public static boolean singleModelOnly() {
        return !"false".equalsIgnoreCase(value("ai.ollama.singleModel", "OLLAMA_SINGLE_MODEL", "true"));
    }

    /** 模型空闲常驻时间，透传给 Ollama 的 keep_alive */
    public static String keepAlive() {
        return value("ai.ollama.keepAlive", "OLLAMA_KEEP_ALIVE", "5m");
    }

    // ---------------- 识图 ----------------

    /** 两段式识别：先把图片转写成文字，再交给文字模型结构化；关闭则回到一次性 JSON */
    public static boolean twoStageRecognize() {
        return !"false".equalsIgnoreCase(value("ai.ocr.twoStage", "AI_OCR_TWO_STAGE", "true"));
    }

    /** 识图前的图片预处理：EXIF 纠正、分辨率归一化、超长图切带 */
    public static boolean imagePreprocess() {
        return !"false".equalsIgnoreCase(value("ai.ocr.preprocess", "AI_OCR_PREPROCESS", "true"));
    }

    /** 视觉模型单次最大输出 token（复杂公式题比纯文本长） */
    public static int visionMaxTokens() {
        return parseInt(value("ai.vision.maxTokens", "AI_VISION_MAX_TOKENS", "3000"), 3000);
    }

    /** 视觉模型上下文长度，给图片 token + 长提示词留足空间 */
    public static int visionNumCtx() {
        return parseInt(value("ai.vision.numCtx", "AI_VISION_NUM_CTX", "8192"), 8192);
    }

    public static double visionTemperature() {
        try {
            return Double.parseDouble(value("ai.vision.temperature", "AI_VISION_TEMPERATURE", "0.1"));
        } catch (Exception e) {
            return 0.1;
        }
    }

    // ---------------- 通用参数 ----------------

    public static int timeoutSeconds() {
        return parseInt(value("ai.timeout.seconds", "AI_TIMEOUT_SECONDS", "90"), 90);
    }

    public static int maxTokens() {
        return parseInt(value("ai.max.tokens", "AI_MAX_TOKENS", "900"), 900);
    }

    public static double temperature() {
        try {
            return Double.parseDouble(value("ai.temperature", "AI_TEMPERATURE", "0.4"));
        } catch (Exception e) {
            return 0.4;
        }
    }

    // ---------------- 联网校验（上传错题识别后的可信度核对） ----------------

    /** 服务端总开关，关闭后 aiVerify 直接返回不可用 */
    public static boolean searchEnabled() {
        return !"false".equalsIgnoreCase(value("ai.search.enabled", "AI_SEARCH_ENABLED", "true"));
    }

    /** 每次检索保留的条数 */
    public static int searchTopN() {
        return parseInt(value("ai.search.topN", "AI_SEARCH_TOP_N", "5"), 5);
    }

    /** 注入校验提示词的证据字数上限：默认档摘要少，AnySearch 正文多 */
    public static int searchMaxChars(String tier) {
        boolean any = "anysearch".equalsIgnoreCase(tier == null ? "" : tier.trim());
        return parseInt(any
                ? value("ai.search.anysearch.maxChars", "ANYSEARCH_MAX_CHARS", "2500")
                : value("ai.search.default.maxChars", "AI_SEARCH_MAX_CHARS", "1500"), any ? 2500 : 1500);
    }

    /** 检索超时：默认档要求快，AnySearch 档可稍等 */
    public static int searchTimeoutMs(String tier) {
        boolean any = "anysearch".equalsIgnoreCase(tier == null ? "" : tier.trim());
        return parseInt(any
                ? value("ai.search.anysearch.timeoutMs", "ANYSEARCH_TIMEOUT_MS", "8000")
                : value("ai.search.default.timeoutMs", "AI_SEARCH_TIMEOUT_MS", "4000"), any ? 8000 : 4000);
    }

    public static String anySearchBase() {
        return trimSlash(value("ai.search.anysearch.baseUrl", "ANYSEARCH_BASE", "https://api.anysearch.com/v1"));
    }

    /** 服务端兜底 Key；前端填写的 Key 优先 */
    public static String anySearchApiKey() {
        return value("ai.search.anysearch.apiKey", "ANYSEARCH_KEY", "");
    }

    public static int searchCacheSize() {
        return parseInt(value("ai.search.cache.size", "AI_SEARCH_CACHE_SIZE", "50"), 50);
    }

    public static long searchCacheTtlMs() {
        return parseInt(value("ai.search.cache.ttlMinutes", "AI_SEARCH_CACHE_TTL_MINUTES", "1440"), 1440) * 60_000L;
    }

    // ---------------- 工具方法 ----------------

    /** 文字任务使用的模型名 */
    public static String modelName(String provider) {
        if ("cloud".equals(provider) || "deepseek".equals(provider)) {
            return cloudModel();
        }
        if ("ollama".equals(provider)) {
            return ollamaTextModel();
        }
        return "local-rule";
    }

    /** 识图任务使用的模型名：固定本机 Ollama 视觉模型 */
    public static String visionModelName(String provider) {
        return ollamaVisionModel();
    }

    public static String baseUrl(String provider) {
        return ("cloud".equals(provider) || "deepseek".equals(provider)) ? cloudBaseUrl() : ollamaBaseUrl();
    }

    /** 云端请求需要带 Bearer Key，本机 Ollama 不需要 */
    public static String apiKey(String provider) {
        return ("cloud".equals(provider) || "deepseek".equals(provider)) ? cloudApiKey() : "";
    }

    /** 探测 host:port 是否可连接（300ms 超时，失败视为不可用） */
    public static boolean reachable(String baseUrl) {
        try {
            URL url = new URL(baseUrl);
            int port = url.getPort() > 0 ? url.getPort() : ("https".equals(url.getProtocol()) ? 443 : 80);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(url.getHost(), port), 300);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }
}
