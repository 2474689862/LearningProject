package com.aistudy.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * AI 能力入口：统一走 OpenAI 兼容的 /chat/completions 协议。
 * DeepSeek 官方 API 与本机 Ollama 均适用；两者都不可用时退回本地规则模板。
 * <p>本机 Ollama 采用双模型分工：文字任务用 qwen3，图片识别用 qwen2.5vl；
 * 为避免两个模型同时占用显存，调用前会先卸载另一个模型（可通过
 * ai.ollama.singleModel=false 关闭）。qwen3 默认关闭思考过程以降低等待时间，
 * 错题解析等需要推理的场景会显式开启。</p>
 */
public class AiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ERROR_SNIPPET = 200;

    /** 串行化本机模型调用，保证同一时刻只有一个模型在显存里工作 */
    private static final ReentrantLock MODEL_LOCK = new ReentrantLock(true);

    /** 单次调用结果 */
    public static class Result {
        public String text;
        public String provider;
        public String model;
        public long elapsedMs;
        public boolean fallback;

        public Result(String text, String provider, String model, long elapsedMs, boolean fallback) {
            this.text = text;
            this.provider = provider;
            this.model = model;
            this.elapsedMs = elapsedMs;
            this.fallback = fallback;
        }
    }

    /**
     * 单轮问答。
     *
     * @param system   系统提示词
     * @param user     用户提示词
     * @param fallback 模型不可用或调用失败时的本地兜底内容（可为 null，此时直接抛异常）
     */
    public Result chat(String system, String user, Supplier<String> fallback) {
        return chat(system, user, false, fallback);
    }

    /**
     * 单轮问答，可指定是否允许输出思考过程。
     *
     * @param think 仅对 qwen3 等支持 /no_think 的推理模型生效：true = 先思考再作答（质量优先），
     *              false = 直接作答（速度优先）
     */
    public Result chat(String system, String user, boolean think, Supplier<String> fallback) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (system != null && !system.trim().isEmpty()) {
            messages.add(role("system", system));
        }
        messages.add(role("user", user));
        return chatMessages(messages, think, fallback);
    }

    /**
     * 多轮对话：messages 依次为 system / user / assistant …，最后一条应为 user。
     */
    public Result chatMessages(List<Map<String, String>> messages, Supplier<String> fallback) {
        return chatMessages(messages, false, fallback);
    }

    /**
     * 多轮对话，可指定是否允许输出思考过程（对话类场景默认关闭，避免等待过久）。
     */
    public Result chatMessages(List<Map<String, String>> messages, boolean think, Supplier<String> fallback) {
        String provider = AiConfig.resolvedProvider();
        if ("local".equals(provider)) {
            if (fallback == null) {
                throw new AiException("尚未配置 AI 模型：请在「设置 → AI 服务」选择本机 Ollama 或填写云端 Key");
            }
            return new Result(fallback.get(), "local", "local-rule", 0L, true);
        }

        long start = System.currentTimeMillis();
        boolean locked = false;
        try {
            if ("ollama".equals(provider)) {
                MODEL_LOCK.lock();
                locked = true;
                // 文字任务：确保显存里是文字模型（另一个识图模型会被卸载）
                switchModel(AiConfig.ollamaTextModel());
            }
            String text = callChatCompletions(provider, messages, think);
            if (text == null || text.trim().isEmpty()) {
                throw new AiException("模型返回内容为空");
            }
            return new Result(plain(text.trim()), provider, AiConfig.modelName(provider),
                    System.currentTimeMillis() - start, false);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            String reason = e instanceof AiException ? e.getMessage() : (e.getMessage() == null ? e.toString() : e.getMessage());
            System.err.println("[AI] 调用 " + provider + " 失败：" + reason);
            if (fallback == null) {
                throw new AiException("AI 调用失败：" + snippet(reason));
            }
            return new Result("（模型调用失败：" + snippet(reason) + "，以下为本地规则生成）\n\n" + fallback.get(),
                    "local", "local-rule", cost, true);
        } finally {
            if (locked) {
                MODEL_LOCK.unlock();
            }
        }
    }

    private String callChatCompletions(String provider, List<Map<String, String>> messages, boolean think) throws Exception {
        String baseUrl = AiConfig.baseUrl(provider);
        URL url = new URL(baseUrl + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(AiConfig.timeoutSeconds() * 1000);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");

        String key = AiConfig.apiKey(provider);
        if (!key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }

        List<Map<String, String>> msgs = new ArrayList<>();
        for (Map<String, String> m : messages) {
            msgs.add(role(m.get("role"), m.get("content")));
        }
        if (!think) {
            disableThinking(provider, msgs);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", AiConfig.modelName(provider));
        payload.put("messages", msgs);
        payload.put("stream", false);
        payload.put("temperature", AiConfig.temperature());
        payload.put("max_tokens", AiConfig.maxTokens());

        byte[] body = MAPPER.writeValueAsBytes(payload);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }

        int status = conn.getResponseCode();
        String response = readAll(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (status >= 400) {
            throw new AiException("HTTP " + status + " " + snippet(response));
        }

        JsonNode root = MAPPER.readTree(response);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiException("响应格式异常 " + snippet(response));
        }
        JsonNode content = choices.get(0).path("message").path("content");
        return content.isMissingNode() ? null : content.asText();
    }

    /**
     * 视觉识别：把图片交给本机 Ollama 视觉模型（如 qwen2.5vl）。
     * 走 Ollama 原生 /api/chat 协议（images 传 base64），兼容性最稳。
     * <p>识图固定走本机：即使文字任务切到云端，也仍然用本地视觉模型，
     * 因为当前云端厂商没有可直接调用的公网视觉接口。</p>
     */
    public Result chatWithImage(String system, String user, byte[] image, Supplier<String> fallback) {
        String provider = "ollama";
        if (!AiConfig.reachable(AiConfig.ollamaBaseUrl())) {
            if (fallback == null) {
                throw new AiException("图片识别固定使用本机 Ollama 视觉模型（云端提供方不支持识图）："
                        + "请先启动 ollama serve，或在「设置 → AI 服务」检查本地模型");
            }
            return new Result(fallback.get(), "local", "local-rule", 0L, true);
        }
        long start = System.currentTimeMillis();
        boolean locked = false;
        try {
            MODEL_LOCK.lock();
            locked = true;
            // 识图任务：确保显存里是视觉模型（文字模型会被卸载）
            switchModel(AiConfig.ollamaVisionModel());
            String text = callOllamaVision(system, user, image);
            if (text == null || text.trim().isEmpty()) {
                throw new AiException("模型返回内容为空");
            }
            return new Result(plain(text.trim()), provider, AiConfig.visionModelName(provider),
                    System.currentTimeMillis() - start, false);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            String reason = e.getMessage() == null ? e.toString() : e.getMessage();
            System.err.println("[AI] 图片识别失败：" + reason);
            if (fallback == null) {
                throw new AiException("图片识别失败：" + snippet(reason));
            }
            return new Result(fallback.get(), "local", "local-rule", cost, true);
        } finally {
            if (locked) {
                MODEL_LOCK.unlock();
            }
        }
    }

    private String callOllamaVision(String system, String user, byte[] image) throws Exception {
        URL url = new URL(nativeBase() + "/api/chat");
        int timeoutMs = Math.max(AiConfig.timeoutSeconds(), 180) * 1000;

        List<Object> messages = new ArrayList<>();
        if (system != null && !system.trim().isEmpty()) {
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", system);
            messages.add(sys);
        }
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", user);
        userMsg.put("images", new String[]{Base64.getEncoder().encodeToString(image)});
        messages.add(userMsg);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", AiConfig.visionTemperature());
        options.put("num_predict", AiConfig.visionMaxTokens());
        options.put("num_ctx", AiConfig.visionNumCtx());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", AiConfig.ollamaVisionModel());
        payload.put("messages", messages);
        payload.put("stream", false);
        payload.put("keep_alive", AiConfig.keepAlive());
        payload.put("options", options);

        String response = postJson(url, MAPPER.writeValueAsBytes(payload), timeoutMs);
        JsonNode root = MAPPER.readTree(response);
        JsonNode content = root.path("message").path("content");
        return content.isMissingNode() ? null : content.asText();
    }

    private String postJson(URL url, byte[] body, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        int status = conn.getResponseCode();
        String response = readAll(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (status >= 400) {
            throw new AiException("HTTP " + status + " " + snippet(response));
        }
        return response;
    }

    /** 模型健康检查结果 */
    public static class Health {
        public boolean ok;
        public String provider;
        public String model;
        public long latencyMs;
        public String reason;
        /** 文字模型正在处理其他请求，本次跳过探测 */
        public boolean busy;
        /** 识图模型名与就绪状态 */
        public String visionModel = "";
        public boolean visionReady = true;

        public Health(boolean ok, String provider, String model, long latencyMs, String reason) {
            this.ok = ok;
            this.provider = provider;
            this.model = model;
            this.latencyMs = latencyMs;
            this.reason = reason;
        }
    }

    private static final long HEALTH_CACHE_MS = 60_000L;
    private static volatile Health healthCache;
    private static volatile long healthCacheAt;

    /**
     * 探测模型是否还在响应：发送一次 max_tokens=1 的最小对话请求。
     * 结果缓存 60 秒，避免刷新页面反复调用；force=true 时跳过缓存（前端「重新检测」）。
     */
    public synchronized Health healthCheck(boolean force) {
        String provider = AiConfig.resolvedProvider();
        String model = AiConfig.modelName(provider);
        String visionModel = AiConfig.visionModelName(provider);

        if ("local".equals(provider)) {
            String reason = AiConfig.hasCloudKey()
                    ? "当前被配置为本地规则模式（ai.provider=local），未接入任何模型"
                    : "未检测到可用模型：云端 Key 为空，且本机 Ollama 未启动";
            Health local = new Health(false, provider, model, 0L, reason);
            local.visionModel = visionModel;
            local.visionReady = true;
            return local;
        }

        Health cached = healthCache;
        if (!force && cached != null && provider.equals(cached.provider)
                && System.currentTimeMillis() - healthCacheAt < HEALTH_CACHE_MS) {
            return cached;
        }

        long start = System.currentTimeMillis();
        long readTimeoutMs = "ollama".equals(provider) ? 20_000L : 12_000L;
        boolean locked = false;
        Health h;
        try {
            if ("ollama".equals(provider)) {
                // 模型正在生成时不去抢占显存，直接返回「忙」状态，避免把正在用的模型卸载
                locked = MODEL_LOCK.tryLock(2, TimeUnit.SECONDS);
                if (!locked) {
                    Health busy = new Health(true, provider, model, 0L, "");
                    busy.busy = true;
                    busy.visionModel = visionModel;
                    busy.visionReady = modelReady("ollama", visionModel);
                    return busy;
                }
            }
            ping(provider, readTimeoutMs);
            h = new Health(true, provider, model, System.currentTimeMillis() - start, "");
        } catch (Exception e) {
            h = new Health(false, provider, model, System.currentTimeMillis() - start, describe(provider, e));
        } finally {
            if (locked) {
                MODEL_LOCK.unlock();
            }
        }

        h.visionModel = visionModel;
        h.visionReady = modelReady("ollama", visionModel);
        if (h.ok && !h.visionReady) {
            h.reason = "识图模型 " + visionModel + " 未就绪（文字模型正常），图片识别会退回本地规则";
        }
        healthCache = h;
        healthCacheAt = System.currentTimeMillis();
        System.err.println("[AI] 健康检查 " + provider + " → "
                + (h.ok ? "正常 " + h.latencyMs + "ms" : "异常：" + h.reason));
        return h;
    }

    /** 以最小代价确认模型还能返回内容 */
    private void ping(String provider, long readTimeoutMs) throws Exception {
        if ("ollama".equals(provider)) {
            String m = AiConfig.modelName(provider);
            if (!ollamaTagsReachable()) {
                throw new AiException("无法连接本机 Ollama（" + AiConfig.ollamaBaseUrl()
                        + "），请确认 ollama serve 正在运行");
            }
            if (!modelReady(provider, m)) {
                throw new AiException("Ollama 已启动，但模型 " + m + " 尚未拉取（或模型目录不一致），请执行：ollama pull " + m);
            }
            // 探测走文字模型：顺便把显存切回文字模型
            switchModel(m);
        }

        URL url = new URL(AiConfig.baseUrl(provider) + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(4_000);
        conn.setReadTimeout((int) readTimeoutMs);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        String key = AiConfig.apiKey(provider);
        if (!key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(role("user", "ping"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", AiConfig.modelName(provider));
        payload.put("messages", messages);
        payload.put("stream", false);
        payload.put("temperature", 0.0);
        payload.put("max_tokens", 1);

        byte[] body = MAPPER.writeValueAsBytes(payload);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }

        int status = conn.getResponseCode();
        String response = readAll(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (status >= 400) {
            throw new HealthException(status, snippet(response));
        }
        JsonNode root = MAPPER.readTree(response);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new AiException("模型返回格式异常：" + snippet(response));
        }
    }

    /** 把探测异常转成用户能看懂的原因 */
    private static String describe(String provider, Exception e) {
        if (e instanceof HealthException) {
            int status = ((HealthException) e).status;
            String detail = ((HealthException) e).detail;
            if (status == 401 || status == 403) {
                return "API Key 无效或无权限（HTTP " + status + "），请到「设置 → AI 服务」检查 Key 与厂商";
            }
            if (status == 402) {
                return "账户余额不足（HTTP 402），充值后即可恢复";
            }
            if (status == 429) {
                return "请求过于频繁被限流（HTTP 429），请稍后再试";
            }
            if (status >= 500) {
                return "模型服务端异常（HTTP " + status + "），请稍后重试";
            }
            return "模型服务返回异常（HTTP " + status + "）" + (detail.isEmpty() ? "" : "：" + detail);
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return "模型在限定时间内没有响应（超时），可能正在加载模型或网络拥塞，可稍后点「重新检测」";
        }
        if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
            return "ollama".equals(provider)
                    ? "无法连接本机 Ollama，请确认 ollama serve 正在运行"
                    : "无法连接云端接口，请检查网络、代理或 Base URL";
        }
        String msg = e.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? e.toString() : msg;
    }

    /** 带 HTTP 状态码的探测异常，便于给出针对性提示 */
    private static class HealthException extends RuntimeException {
        final int status;
        final String detail;

        HealthException(int status, String detail) {
            super("HTTP " + status);
            this.status = status;
            this.detail = detail == null ? "" : detail;
        }
    }

    /** 原生 /api/tags 是否可访问：区分「服务没起来」和「模型没拉取」 */
    private static boolean ollamaTagsReachable() {
        try {
            String nativeBase = AiConfig.ollamaBaseUrl().replaceAll("/v1$", "");
            HttpURLConnection conn = (HttpURLConnection) new URL(nativeBase + "/api/tags").openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("Accept", "application/json");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 本机 Ollama 是否已拉取指定模型（查询原生 /api/tags）。
     * 非 ollama 提供方直接返回 true。
     */
    public static boolean modelReady(String provider, String model) {
        if (!"ollama".equals(provider)) {
            return true;
        }
        try {
            String nativeBase = AiConfig.ollamaBaseUrl().replaceAll("/v1$", "");
            HttpURLConnection conn = (HttpURLConnection) new URL(nativeBase + "/api/tags").openConnection();
            conn.setConnectTimeout(800);
            conn.setReadTimeout(1500);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() >= 400) {
                return false;
            }
            JsonNode root = MAPPER.readTree(readAll(conn.getInputStream()));
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                return false;
            }
            JsonNode list = models;
            for (JsonNode m : list) {
                String name = m.path("name").asText("");
                if (name.equals(model) || name.equals(model + ":latest")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ollama 原生接口地址（去掉 OpenAI 兼容的 /v1 后缀） */
    private static String nativeBase() {
        return AiConfig.ollamaBaseUrl().replaceAll("/v1$", "");
    }

    /** 查询当前已加载到显存的模型（Ollama /api/ps） */
    private List<String> runningModels() {
        List<String> names = new ArrayList<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(nativeBase() + "/api/ps").openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() >= 400) {
                return names;
            }
            JsonNode root = MAPPER.readTree(readAll(conn.getInputStream()));
            for (JsonNode m : root.path("models")) {
                String name = m.path("name").asText("");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (Exception ignored) {
            // Ollama 未启动时按「没有已加载模型」处理
        }
        return names;
    }

    /** 卸载指定模型：keep_alive=0 让 Ollama 立即回收显存 */
    private void unloadModel(String model) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("keep_alive", 0);
            postJson(new URL(nativeBase() + "/api/generate"), MAPPER.writeValueAsBytes(payload), 8000);
            System.err.println("[AI] 已卸载模型 " + model + "，释放显存");
        } catch (Exception e) {
            System.err.println("[AI] 卸载模型 " + model + " 失败：" + e.getMessage());
        }
    }

    /** 模型名比较，兼容省略 :latest 的写法 */
    private static boolean sameModel(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b) || a.equals(b + ":latest") || b.equals(a + ":latest");
    }

    /**
     * 双模型互斥：加载目标模型前，把显存里其他模型卸载掉。
     * 文字任务目标为 deepseek-r1，识图任务目标为 qwen2.5vl，
     * 因此两个模型不会同时占用显存（ai.ollama.singleModel=false 可关闭）。
     */
    private void switchModel(String target) {
        if (!AiConfig.singleModelOnly() || target == null || target.isEmpty()) {
            return;
        }
        List<String> running = runningModels();
        boolean targetLoaded = false;
        for (String name : running) {
            if (sameModel(name, target)) {
                targetLoaded = true;
                continue;
            }
            unloadModel(name);
        }
        if (!targetLoaded) {
            System.err.println("[AI] 切换模型 → " + target
                    + (running.isEmpty() ? "（当前没有已加载模型）" : "（已释放其他模型）"));
        }
    }

    /**
     * 把模型输出里的 LaTeX 片段转成纯文本，便于页面直接阅读。
     * 例如 \( x^2 \) → x²，\frac{1}{2} → 1/2，\infty → ∞。
     */
    private static String plain(String raw) {
        String s = stripThinking(raw);
        s = s.replaceAll("\\\\[()\\[\\]]", "");
        s = s.replace("$$", "");
        s = s.replace("\\left", "").replace("\\right", "");
        s = s.replaceAll("\\\\[dt]?ext\\{([^{}]*)\\}", "$1");
        s = s.replaceAll("\\\\[dt]?frac\\{([^{}]*)\\}\\{([^{}]*)\\}", "$1/$2");
        s = s.replaceAll("\\\\sqrt\\{([^{}]*)\\}", "√$1");
        s = s.replaceAll("\\^\\{?2\\}?", "²");
        s = s.replaceAll("\\^\\{?3\\}?", "³");
        s = s.replaceAll("\\^\\{?([0-9a-zA-Z+]+)\\}?", "^$1");
        s = s.replace("\\leq", "≤").replace("\\geq", "≥").replace("\\neq", "≠");
        s = s.replace("\\le", "≤").replace("\\ge", "≥").replace("\\ne", "≠");
        s = s.replace("\\infty", "∞").replace("\\pi", "π").replace("\\alpha", "α")
                .replace("\\beta", "β").replace("\\theta", "θ").replace("\\Delta", "Δ")
                .replace("\\times", "×").replace("\\cdot", "·").replace("\\div", "÷")
                .replace("\\pm", "±").replace("\\in", "∈").replace("\\sum", "Σ")
                .replace("\\to", "→").replace("\\Rightarrow", "⇒").replace("\\approx", "≈");
        s = s.replaceAll("\\\\begin\\{[^}]*\\}", "").replaceAll("\\\\end\\{[^}]*\\}", "");
        s = s.replaceAll("\\\\\\s+", " ");
        s = s.replaceAll("[ \\t]+\\n", "\n");
        return s.trim();
    }

    /** 推理模型思考段标签（分开拼接，避免被当成标记） */
    private static final String THINK_OPEN = "<thi" + "nk>";
    private static final String THINK_CLOSE = "</thi" + "nk>";

    /**
     * deepseek-r1 等推理模型会在正文前输出思考过程（包在 think 标签里），
     * 这里去掉思考段，只保留最终答案给页面展示。
     */
    private static String stripThinking(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw;
        int open;
        while ((open = s.indexOf(THINK_OPEN)) >= 0) {
            int close = s.indexOf(THINK_CLOSE, open);
            if (close < 0) {
                s = s.substring(0, open);
                break;
            }
            s = s.substring(0, open) + s.substring(close + THINK_CLOSE.length());
        }
        return s.trim();
    }

    /**
     * qwen3 系列支持在最后一条用户消息末尾追加 /no_think 关闭思考过程，直接输出答案。
     * 非 qwen3 模型（或 DeepSeek 接口）不加，避免污染提示词。
     */
    private static void disableThinking(String provider, List<Map<String, String>> msgs) {
        if (!"ollama".equals(provider)
                || !AiConfig.ollamaTextModel().toLowerCase(Locale.ROOT).startsWith("qwen3")) {
            return;
        }
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Map<String, String> m = msgs.get(i);
            if ("user".equals(m.get("role"))) {
                m.put("content", m.get("content") + "\n/no_think");
                return;
            }
        }
    }

    /** 构造一条对话消息，供 Action 层拼装历史上下文 */
    public static Map<String, String> role(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= ERROR_SNIPPET ? t : t.substring(0, ERROR_SNIPPET) + "…";
    }

    /** AI 调用异常，message 可直接展示给前端 */
    public static class AiException extends RuntimeException {
        public AiException(String message) {
            super(message);
        }
    }
}
