package com.aistudy.action;

import com.aistudy.ai.AiConfig;
import com.aistudy.ai.AiService;
import com.aistudy.ai.LocalAdvisor;
import com.aistudy.ai.QuestionVerifier;
import com.aistudy.search.SearchHit;
import com.aistudy.search.SearchService;
import com.aistudy.util.ImagePreprocessor;
import com.aistudy.util.LatexNormalizer;
import com.aistudy.util.UploadPaths;
import com.aistudy.dao.AiDao;
import com.aistudy.dao.CatalogDao;
import com.aistudy.dao.QuestionDao;
import com.aistudy.dao.SummaryDao;
import com.aistudy.model.Category;
import com.aistudy.model.Question;
import com.aistudy.model.Subject;
import com.aistudy.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 板块：错题解析、学习建议、自由提问。
 * 通过 OpenAI 兼容协议对接云端 API（默认 DeepSeek 档案）或本机 Ollama 的 qwen 系列模型，
 * 两者都不可用时自动退回本地规则模板，保证 Demo 可演示。
 */
public class AiAction extends BaseAction {

    private static final String SYSTEM_EXPLAIN =
            "你是一位经验丰富的中小学学科老师，擅长把错题讲透。"
                    + "请使用简体中文、面向学生作答；不要编造题干中没有的条件；"
                    + "不要输出 Markdown 表格或代码块，用短段落和编号列表；总字数控制在 500 字以内。";

    private static final String SYSTEM_ADVICE =
            "你是一位学习规划老师。根据学生的错题统计数据，用简体中文给出具体、可执行的复习建议；"
                    + "不要空话套话，不要输出 Markdown 表格；总字数控制在 400 字以内。";

    private static final String SYSTEM_ASK =
            "你是「AI 学习助手」，面向中小学生的答疑老师。用简体中文回答，先给结论再给简短理由，"
                    + "不确定的内容要明确说明，不要输出 Markdown 表格；总字数控制在 400 字以内。";

    private static final String SYSTEM_IMAGE_ASK =
            "你是「AI 学习助手」的看图答疑老师。先客观描述图片里有什么（题目、图表、手写内容都可以），"
            + "再回答学生的问题；看不清的地方直接说明，不要编造条件。用简体中文分段输出，"
            + "不要输出 Markdown 表格或代码块，总字数控制在 500 字以内。";

    private static final String SYSTEM_CHAT =
            "你是「AI 学习助手」里的陪学老师，正在和学生一对一聊天。要求：\n"
                    + "1. 用简体中文，语气自然、鼓励式，像真人老师；\n"
                    + "2. 必须结合上文回答，学生说“再讲讲/举个例子/为什么”这类追问时，先承接上一轮内容；\n"
                    + "3. 先给结论，再给 2-4 条理由或步骤，必要时给一个小例子；\n"
                    + "4. 不要输出 Markdown 表格、代码块，不要重复学生的问题；\n"
                    + "5. 每次回答控制在 300 字以内，不确定的内容明确说明。";

    private static final String SYSTEM_CLASSIFY =
            "你是中小学错题归档助手。根据题目标题和题干，从给定的学科、知识点列表中选出最匹配的一项，并判断难度。\n"
                    + "规则：\n"
                    + "1. 学科必须来自给定学科列表；知识点必须来自该学科下的知识点列表；无法判断时填 null；\n"
                    + "2. difficulty 取值 1（简单）/2（中等）/3（困难）；\n"
                    + "3. 只输出一个 JSON 对象，不要解释、不要 Markdown 代码块、不要多余文字；\n"
                    + "4. 输出格式：{\"subject\":\"数学\",\"category\":\"函数与导数\",\"difficulty\":2,\"reason\":\"一句话依据\"}";

    private static final String SYSTEM_RECOGNIZE =
            "你是错题录入助手。请先准确识别图片中的题目文字（含公式、选项、手写作答），再整理成录入信息。\n"
                    + "只输出一个 JSON 对象，不要解释、不要 Markdown 代码块、不要多余文字。\n"
                    + "字段说明：\n"
                    + "title：不超过 30 字的题目标题，概括考点与题型；\n"
                    + "content：题干文字，保留原题号与小问，公式用 LaTeX（$...$）；选择题不要把选项混进题干；\n"
                    + "type：题型，必须是 choice（选择题）/judge（判断题）/blank（填空题）/short（简答题）"
                    + "/essay（解答题、大题）/reading（阅读理解）/experiment（实验题）/writing（写作、作文）"
                    + "/listening（听力题）/translation（翻译题）/calculation（计算题）之一；\n"
                    + "options：选择题的选项数组，每项形如 \"A. 内容\"，按图片顺序；不是选择题就给空数组 [];\n"
                    + "correctAnswer：题目本身的标准答案，选择题填选项字母（如 B），判断题填 正确 或 错误，"
                    + "填空题按空顺序写答案，简答/解答题写参考答案要点；图片里没有标准答案就填空字符串；\n"
                    + "answer：图片中学生写下的作答，没有则空字符串；\n"
                    + "analysis：图片中可见的批改/错因痕迹，没有则空字符串；\n"
                    + "source：试卷、章节或作业来源，没有则空字符串；\n"
                    + "difficulty：1=简单、2=中等、3=困难；\n"
                    + "subject：这道题所属学科的标准名称（如 数学、语文、英语、物理、化学、生物、政治、历史、地理），"
                    + "必须是具体学科名，不要填 null；若属于给定列表之外的学科，也要给出你认为最准确的学科名；\n"
                    + "category：该学科下的知识点名称，优先从给定列表中选择；列表里没有合适的，就给出你建议的知识点名。\n"
                    + "输出格式：{\"title\":\"\",\"content\":\"\",\"type\":\"choice\",\"options\":[\"A. \",\"B. \"],"
                    + "\"correctAnswer\":\"\",\"answer\":\"\",\"analysis\":\"\",\"source\":\"\","
                    + "\"difficulty\":2,\"subject\":\"\",\"category\":null}";

    /** 第一段：只做图片转写，公式保留 LaTeX，避免模型同时兼顾 JSON 与公式 */
    private static final String SYSTEM_OCR =
            "你是题目图片转写器。逐行转写图片中可见的全部文字：题号、小问、题干、选项、公式、手写作答与批改痕迹都要保留。\n"
                    + "公式一律用 LaTeX：行内用 $...$，独立公式用 $$...$$；分式、根号、上下标必须写完整，"
                    + "例如 $\\frac{x^2}{a^2}+y^2=1$、$\\sqrt{2}$、$t_0$。\n"
                    + "禁止用斜杠表示分式（不要写 x²/a²），禁止用 Unicode 上标（不要写 x²），"
                    + "一律写成 $\\frac{x^2}{a^2}$、$x^{2}$。\n"
                    + "不要解释、不要总结、不要翻译、不要补充图里没有的内容；看不清的字用 □ 代替。\n"
                    + "只输出转写文本。";

    /** 第二段：把转写文本结构化成录入字段 */
    private static final String SYSTEM_STRUCTURE =
            "你是错题录入助手。下面是从题目图片转写出的文字，请整理成录入信息。\n"
                    + "只输出一个 JSON 对象，不要解释、不要 Markdown 代码块、不要多余文字。\n"
                    + "字段说明：\n"
                    + "title：不超过 30 字的题目标题，概括考点与题型；\n"
                    + "content：题干文字，保留原题号与小问，公式保留 LaTeX（$...$），不要改写成纯文本；"
                    + "选择题不要把选项混进题干；\n"
                    + "type：题型，必须是 choice（选择题）/judge（判断题）/blank（填空题）/short（简答题）"
                    + "/essay（解答题、大题）/reading（阅读理解）/experiment（实验题）/writing（写作、作文）"
                    + "/listening（听力题）/translation（翻译题）/calculation（计算题）之一；\n"
                    + "options：选择题的选项数组，每项形如 \"A. 内容\"，按图片顺序；不是选择题就给空数组 [];\n"
                    + "correctAnswer：题目本身的标准答案；转写里没有标准答案就填空字符串，不要自己解题补答案；\n"
                    + "answer：转写中学生写下的作答，没有则空字符串；\n"
                    + "analysis：转写中可见的批改/错因痕迹，没有则空字符串；\n"
                    + "source：试卷、章节或作业来源，没有则空字符串；\n"
                    + "difficulty：1=简单、2=中等、3=困难；\n"
                    + "subject：这道题所属学科的标准名称（如 数学、语文、英语、物理、化学、生物、政治、历史、地理），"
                    + "必须是具体学科名，不要填 null；\n"
                    + "category：该学科下的知识点名称，优先从给定列表中选择；列表里没有合适的，就给出你建议的知识点名。\n"
                    + "转写里没有的信息一律留空，不要编造条件、选项或答案。\n"
                    + "公式要保留 LaTeX；转写里的 x²/a²、√2 这类非标准写法，请改写为 "
                    + "$\\frac{x^2}{a^2}$、$\\sqrt{2}$。\n"
                    + "输出格式：{\"title\":\"\",\"content\":\"\",\"type\":\"essay\",\"options\":[],"
                    + "\"correctAnswer\":\"\",\"answer\":\"\",\"analysis\":\"\",\"source\":\"\","
                    + "\"difficulty\":2,\"subject\":\"\",\"category\":null}";

    private static final String SYSTEM_ANALYZE =
            "你是一位学科教研老师，正在帮学生复盘一道错题。用简体中文作答，面向学生、具体可执行。\n"
                    + "请严格按以下四段输出，不要输出 Markdown 表格或代码块，总字数控制在 450 字以内：\n"
                    + "1. 考点定位：这道题真正考什么（1-2 句）\n"
                    + "2. 错因诊断：结合我的作答指出问题出在哪一步（2-3 句）\n"
                    + "3. 正确解法：分步骤讲清关键转折点（3-5 步）\n"
                    + "4. 改进动作：今天就能做的 1-2 件事";

    private static final String SYSTEM_SUMMARY =
            "你是一位学习数据分析老师。根据学生的错题本数据做阶段性总结，用简体中文，具体、有依据、不谈空话。\n"
                    + "请按以下结构输出，不要输出 Markdown 表格或代码块，总字数控制在 550 字以内：\n"
                    + "1. 整体判断：用 2-3 句概括当前学习状态与主要问题\n"
                    + "2. 薄弱环节：按学科/知识点指出最该优先补的 2-3 项，并说明依据（引用具体题量）\n"
                    + "3. 错因共性：从错题标题与难度分布推测主要失分类型\n"
                    + "4. 下周计划：给出可执行的安排（学科 + 知识点 + 题量）";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 关键词规则兜底用的学科词表 */
    private static final Map<String, String[]> SUBJECT_KEYWORDS = new LinkedHashMap<>();

    static {
        SUBJECT_KEYWORDS.put("数学", new String[]{"函数", "导数", "单调", "极值", "三角", "数列", "向量",
                "立体几何", "解析几何", "圆锥曲线", "椭圆", "概率", "排列组合", "不等式", "方程", "集合",
                "对数", "指数", "积分", "几何"});
        SUBJECT_KEYWORDS.put("语文", new String[]{"阅读", "文言", "古诗", "诗词", "作文", "字音", "字形",
                "病句", "成语", "修辞", "默写", "散文", "小说"});
        SUBJECT_KEYWORDS.put("英语", new String[]{"grammar", "vocabulary", "完形", "阅读", "语法", "时态",
                "从句", "单词", "作文", "写作", "听力", "翻译", "english"});
        SUBJECT_KEYWORDS.put("物理", new String[]{"力", "加速度", "运动", "速度", "动能", "势能", "机械能",
                "电场", "磁场", "电路", "电阻", "电磁", "压强", "浮力", "光学", "折射", "实验"});
        SUBJECT_KEYWORDS.put("化学", new String[]{"化学式", "元素", "氧化", "还原", "溶液", "浓度", "摩尔",
                "反应", "有机物", "烃", "醇", "酸碱", "电解", "实验", "化合价", "离子"});
    }

    private final AiService aiService = new AiService();
    private final AiDao aiDao = new AiDao();
    private final QuestionDao questionDao = new QuestionDao();
    private final CatalogDao catalogDao = new CatalogDao();
    private final SummaryDao summaryDao = new SummaryDao();

    private Long questionId;
    private String question;
    private String analysis;
    private String message;
    private String history;
    private String title;
    private String content;
    private String imagePath;
    /** 健康检查：传 1 表示跳过缓存重新探测 */
    private String force;
    /** 联网校验档位：default / anysearch */
    private String searchTier;
    /** 前端填写的 AnySearch Key：仅本次请求使用，不落库、不写日志 */
    private String anySearchKey;
    /** 校验入参：题型、选项、识别出的答案、学科名 */
    private String type;
    private String options;
    private String correctAnswer;
    private String subjectName;

    /** 当前 AI 运行状态 */
    public String status() {
        try {
            User user = currentUser();
            String provider = AiConfig.resolvedProvider();
            String model = AiConfig.modelName(provider);
            String visionModel = AiConfig.visionModelName(provider);
            boolean modelReady = AiService.modelReady(provider, model);
            // 识图固定走本机 Ollama，云端提供方也要按本地模型判断
            boolean visionReady = AiService.modelReady("ollama", visionModel);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("provider", provider);
            data.put("providerLabel", label(provider));
            data.put("model", model);
            data.put("visionModel", visionModel);
            data.put("visionReady", visionReady);
            data.put("singleModel", AiConfig.singleModelOnly());
            data.put("fallback", "local".equals(provider));
            data.put("modelReady", modelReady);
            data.put("keyConfigured", AiConfig.hasCloudKey());
            data.put("cloudVendor", AiConfig.cloudVendor());
            data.put("cloudVendorLabel", AiConfig.cloudVendorLabel());
            data.put("ollamaReachable", AiConfig.reachable(AiConfig.ollamaBaseUrl()));
            data.put("history", aiDao.recent(user.id, 5));
            String tip;
            if ("local".equals(provider)) {
                tip = "未检测到可用模型，当前为本地规则模式。可到「设置 → AI 服务」选择本机 Ollama 或填写云端 Key。";
            } else if (!modelReady || !visionReady) {
                tip = "已连接 " + label(provider) + "：文字 " + model + "（" + (modelReady ? "就绪" : "未就绪，请 ollama pull " + model)
                        + "），识图 " + visionModel + "（" + (visionReady ? "就绪" : "未就绪，请 ollama pull " + visionModel) + "）";
            } else {
                tip = "已连接 " + label(provider) + "：文字任务用 " + model + "，图片识别用 " + visionModel
                        + (AiConfig.singleModelOnly() ? "；同一时刻只加载一个模型，切换时自动释放显存" : "");
            }
            data.put("tip", tip);
            return ok(data);
        } catch (Exception e) {
            return fail("获取 AI 状态失败：" + e.getMessage());
        }
    }

    /**
     * 模型健康检查：AI 相关页面进入时自动调用一次。
     * 真实发起一次最小对话请求，模型无响应时返回 ok=false + reason，前端据此提示用户。
     */
    public String health() {
        try {
            currentUser();
            boolean forceRefresh = "1".equals(force) || "true".equalsIgnoreCase(force);
            AiService.Health h = aiService.healthCheck(forceRefresh);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", h.ok);
            data.put("provider", h.provider);
            data.put("providerLabel", label(h.provider));
            data.put("model", h.model);
            data.put("visionModel", h.visionModel);
            data.put("visionReady", h.visionReady);
            data.put("busy", h.busy);
            data.put("latencyMs", h.latencyMs);
            data.put("reason", h.reason == null ? "" : h.reason);
            data.put("fallback", !h.ok);
            data.put("checkedAt", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date()));
            String tip;
            if (h.busy) {
                tip = "模型正在处理其他请求，本次跳过重复检测";
            } else if (!h.ok) {
                tip = "AI 生成会退回本地规则模板，结果可能不够准确";
            } else if (!h.visionReady) {
                tip = "文字模型 " + h.model + " 正常，识图模型 " + h.visionModel + " 未就绪";
            } else {
                tip = "文字模型 " + h.model + " 响应正常（" + h.latencyMs + " ms），识图模型 " + h.visionModel + " 已就绪";
            }
            data.put("tip", tip);
            return ok(data);
        } catch (Exception e) {
            return fail("AI 健康检查失败：" + e.getMessage());
        }
    }

    /** 错题解析 */
    public String explain() {
        try {
            User user = currentUser();
            if (questionId == null) {
                return fail("请选择要解析的错题");
            }
            Question q = questionDao.findById(questionId, user.id);
            if (q == null) {
                return fail("题目不存在或无权限");
            }
            String prompt = buildExplainPrompt(q);
            // 错题解析需要多步推理，开启 qwen3 思考过程（质量优先）
            AiService.Result result = aiService.chat(SYSTEM_EXPLAIN, prompt, true, () -> LocalAdvisor.explain(q));
            long logId = log(user.id, q.id, "explain", prompt, result);
            return ok(result.fallback ? "已生成本地规则解析" : "解析生成成功", body(result, logId, q.id));
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("生成解析失败：" + e.getMessage());
        }
    }

    /** 基于错题统计的学习建议 */
    public String advice() {
        try {
            User user = currentUser();
            long total = questionDao.countByUser(user.id);
            long today = questionDao.countToday(user.id);
            List<Map<String, Object>> subjectStats = questionDao.countGroupBySubject(user.id);
            List<Map<String, Object>> topCategories = questionDao.countGroupByCategory(user.id, 3);
            String prompt = buildAdvicePrompt(total, today, subjectStats, topCategories);
            AiService.Result result = aiService.chat(SYSTEM_ADVICE, prompt,
                    () -> LocalAdvisor.advice(total, today, subjectStats, topCategories));
            long logId = log(user.id, null, "advice", prompt, result);
            return ok(result.fallback ? "已生成本地规则建议" : "建议生成成功", body(result, logId, null));
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("生成建议失败：" + e.getMessage());
        }
    }

    /** 自由提问（学习中遇到的问题） */
    public String ask() {
        try {
            User user = currentUser();
            String q = question == null ? "" : question.trim();
            if (q.isEmpty()) {
                return fail("请输入你的问题");
            }
            if (q.length() > 500) {
                return fail("问题不能超过 500 字");
            }
            AiService.Result result = aiService.chat(SYSTEM_ASK, q, () -> LocalAdvisor.ask(q));
            long logId = log(user.id, null, "ask", q, result);
            return ok(body(result, logId, null));
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("AI 回答失败：" + e.getMessage());
        }
    }

    /** 多轮聊天：前端传最近若干轮历史，模型据此承接上下文 */
    public String chat() {
        try {
            User user = currentUser();
            String msg = message == null ? "" : message.trim();
            if (msg.isEmpty()) {
                return fail("请输入要问的内容");
            }
            if (msg.length() > 1000) {
                return fail("单条消息不能超过 1000 字");
            }
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(AiService.role("system", SYSTEM_CHAT + "\n\n" + buildProfileContext(user)));
            messages.addAll(parseHistory(history, 8));
            messages.add(AiService.role("user", msg));

            AiService.Result result = aiService.chatMessages(messages,
                    () -> LocalAdvisor.ask(msg));
            long logId = log(user.id, null, "chat", msg, result);
            return ok(result.fallback ? "已生成本地规则回答" : "ok", body(result, logId, null));
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("AI 回答失败：" + e.getMessage());
        }
    }

    /**
     * 把学生的错题统计压缩成一段背景资料拼进对话的系统提示，
     * 让「学习上有什么改进」这类问题能基于真实数据回答，而不是泛泛而谈。
     */
    private String buildProfileContext(User user) {
        long total = questionDao.countByUser(user.id);
        long today = questionDao.countToday(user.id);
        StringBuilder sb = new StringBuilder();
        sb.append("【学生真实数据，涉及学习情况时必须引用，不要编造】\n");
        if (total <= 0) {
            sb.append("该学生还没有录入任何错题，相关建议只能给通用的学习习惯方法。\n");
            return sb.toString();
        }
        sb.append("错题总数：").append(total).append(" 道，今日新增：").append(today).append(" 道\n");
        List<Map<String, Object>> subjectStats = questionDao.countGroupBySubject(user.id);
        sb.append("学科分布：");
        if (subjectStats == null || subjectStats.isEmpty()) {
            sb.append("暂无\n");
        } else {
            for (Map<String, Object> row : subjectStats) {
                sb.append(row.get("name")).append(" ").append(row.get("count")).append(" 道；");
            }
            sb.append("\n");
        }
        List<Map<String, Object>> topCategories = questionDao.countGroupByCategory(user.id, 5);
        sb.append("高频知识点：");
        if (topCategories == null || topCategories.isEmpty()) {
            sb.append("暂无\n");
        } else {
            for (Map<String, Object> row : topCategories) {
                sb.append(row.get("name")).append(" ").append(row.get("count")).append(" 道；");
            }
            sb.append("\n");
        }
        sb.append("统计时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("\n");
        sb.append("回答学习规划、薄弱点、复习顺序类问题时，请点名具体学科和知识点，并给出可执行的下一步。");
        return sb.toString();
    }

    /** 图片问答：上传一张图片（可附一句话问题），交给视觉模型讲解 */
    public String askImage() {
        try {
            User user = currentUser();
            String path = imagePath == null ? "" : imagePath.trim();
            if (path.isEmpty()) {
                return fail("请先上传图片");
            }
            if (!path.startsWith("/uploads/") || path.contains("..")) {
                return fail("图片路径不合法");
            }
            String q = question == null ? "" : question.trim();
            if (q.length() > 500) {
                return fail("问题不能超过 500 字");
            }
            Path resolved = UploadPaths.resolve(path);
            File file = resolved == null ? null : resolved.toFile();
            if (file == null || !file.isFile()) {
                return fail("图片不存在，请重新上传");
            }
            if (file.length() > 10L * 1024 * 1024) {
                return fail("图片超过 10MB，请压缩后重试");
            }
            byte[] bytes = Files.readAllBytes(file.toPath());

            String prompt = q.isEmpty()
                    ? "请先说明这张图片的内容，再讲解其中涉及的知识点。"
                    : "请结合图片内容回答问题：" + q;
            AiService.Result result = aiService.chatWithImage(SYSTEM_IMAGE_ASK, prompt, bytes,
                    () -> LocalAdvisor.ask(q.isEmpty() ? "请讲解图片中的题目" : q));
            long logId = log(user.id, null, "image-ask", prompt, result);

            Map<String, Object> data = body(result, logId, null);
            data.put("imagePath", path);
            data.put("question", q);
            return ok(result.fallback ? "图片识别不可用，已用本地规则回答" : "ok", data);
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("图片问答失败：" + e.getMessage());
        }
    }

    /** 录入题目时自动分类：AI 判断学科、知识点与难度，失败时退回关键词规则 */
    public String classify() {
        try {
            User user = currentUser();
            String t = title == null ? "" : title.trim();
            String c = content == null ? "" : content.trim();
            if (t.isEmpty() && c.isEmpty()) {
                return fail("请先填写题目标题或题干");
            }
            String text = (t + " " + c).trim();
            if (text.length() > 2000) {
                text = text.substring(0, 2000);
            }
            List<Subject> subjects = catalogDao.listSubjects();
            if (subjects.isEmpty()) {
                return fail("还没有学科数据，请先在分类管理中添加");
            }
            List<Category> categories = catalogDao.listCategories(null, null);

            String prompt = buildClassifyPrompt(t, c, subjects, categories);
            AiService.Result result;
            Match match = null;
            boolean ruleFallback = false;
            try {
                result = aiService.chat(SYSTEM_CLASSIFY, prompt, null);
                match = parseMatch(result.text, subjects, categories);
                if (match == null) {
                    match = ruleMatch(text, subjects, categories);
                    ruleFallback = true;
                    result = new AiService.Result("模型输出无法解析，改用关键词规则：" + match.describe(),
                            "local", "rule-matcher", result.elapsedMs, true);
                }
            } catch (AiService.AiException e) {
                match = ruleMatch(text, subjects, categories);
                ruleFallback = true;
                result = new AiService.Result("模型不可用，改用关键词规则：" + match.describe(),
                        "local", "rule-matcher", 0L, true);
            }

            log(user.id, null, "classify", prompt, result);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("subjectId", match.subject == null ? null : match.subject.id);
            data.put("subjectName", match.subject == null ? null : match.subject.name);
            data.put("categoryId", match.category == null ? null : match.category.id);
            data.put("categoryName", match.category == null ? null : match.category.name);
            data.put("difficulty", match.difficulty);
            data.put("reason", match.reason);
            data.put("provider", result.provider);
            data.put("providerLabel", label(result.provider));
            data.put("model", result.model);
            data.put("elapsedMs", result.elapsedMs);
            data.put("fallback", ruleFallback || result.fallback);
            return ok(match.subject == null ? "未能识别学科，请手动选择" : "分类完成", data);
        } catch (Exception e) {
            return fail("自动分类失败：" + e.getMessage());
        }
    }

    /** 把 AI 解析写回题目 */
    public String saveAnalysis() {
        try {
            User user = currentUser();
            if (questionId == null) {
                return fail("参数错误");
            }
            String text = analysis == null ? "" : analysis.trim();
            if (text.isEmpty()) {
                return fail("解析内容为空");
            }
            if (text.length() > 8000) {
                text = text.substring(0, 8000);
            }
            int rows = questionDao.updateAnalysis(questionId, user.id, text);
            return rows > 0 ? ok("已写入该题解析", null) : fail("题目不存在或无权限");
        } catch (Exception e) {
            return fail("保存失败：" + e.getMessage());
        }
    }

    /** 一键识图：读取已上传的题目图片，让视觉模型抽出结构化错题信息 */
    public String recognize() {
        try {
            User user = currentUser();
            String path = imagePath == null ? "" : imagePath.trim();
            if (path.isEmpty()) {
                return fail("请先上传题目图片");
            }
            if (!path.startsWith("/uploads/") || path.contains("..")) {
                return fail("图片路径不合法");
            }
            Path resolved = UploadPaths.resolve(path);
            File file = resolved == null ? null : resolved.toFile();
            if (file == null || !file.isFile()) {
                return fail("图片不存在，请重新上传");
            }
            if (file.length() > 10L * 1024 * 1024) {
                return fail("图片超过 10MB，请压缩后重试");
            }
            byte[] bytes = Files.readAllBytes(file.toPath());

            List<Subject> subjects = catalogDao.listSubjects();
            List<Category> categories = catalogDao.listCategories(null, null);

            String prompt = "";
            AiService.Result result = null;
            VisionInfo info = null;
            String rawText = "";
            boolean structureOnly = false;

            if (AiConfig.twoStageRecognize()) {
                // 第一段：图片标准化 + 视觉模型转写（公式保留 LaTeX）
                List<byte[]> tiles = AiConfig.imagePreprocess()
                        ? ImagePreprocessor.prepare(bytes)
                        : java.util.Collections.singletonList(bytes);
                rawText = LatexNormalizer.normalize(ocrTiles(tiles));
                if (!rawText.isEmpty()) {
                    // 第二段：文字模型把转写结构化
                    prompt = buildStructurePrompt(subjects, categories, rawText);
                    result = chatStructure(prompt);
                    info = parseVision(result.text, subjects, categories);
                    if (info == null) {
                        result = chatStructure(prompt);
                        info = parseVision(result.text, subjects, categories);
                    }
                    if (info == null) {
                        // 结构化失败但转写成功：先用转写填题干，避免整单失败
                        info = fromRawText(rawText, subjects, categories);
                        structureOnly = true;
                    }
                }
            }

            if (info == null) {
                // 兜底：一次性识图直出 JSON（旧路径）
                prompt = buildRecognizePrompt(subjects, categories);
                result = aiService.chatWithImage(SYSTEM_RECOGNIZE, prompt, bytes, null);
                info = parseVision(result.text, subjects, categories);
                if (info == null) {
                    logFail(user.id, prompt, result);
                    return fail("没能从图片里解析出题目。已尝试放大与文字转写仍失败，"
                            + "请裁剪题目区域或换一张更清晰的照片。模型原始输出：" + preview(result.text));
                }
            }

            long logId = log(user.id, null,
                    structureOnly ? "recognize_structure_fail" : "recognize", prompt, result);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("title", info.title);
            data.put("content", info.content);
            data.put("type", info.type);
            data.put("typeLabel", typeLabel(info.type));
            data.put("options", info.options);
            data.put("correctAnswer", info.correctAnswer);
            data.put("answer", info.answer);
            data.put("analysis", info.analysis);
            data.put("source", info.source);
            data.put("difficulty", info.difficulty);
            data.put("subjectId", info.subject == null ? null : info.subject.id);
            data.put("subjectName", info.subject == null ? null : info.subject.name);
            data.put("suggestSubjectName", info.rawSubject);
            data.put("needCreateSubject", info.subject == null && !info.rawSubject.isEmpty());
            data.put("categoryId", info.category == null ? null : info.category.id);
            data.put("categoryName", info.category == null ? null : info.category.name);
            data.put("suggestCategoryName", info.rawCategory);
            data.put("needCreateCategory", info.subject != null && info.category == null
                    && !info.rawCategory.isEmpty());
            data.put("provider", result.provider);
            data.put("providerLabel", label(result.provider));
            data.put("model", result.model);
            data.put("elapsedMs", result.elapsedMs);
            data.put("fallback", result.fallback);
            data.put("logId", logId);
            data.put("rawText", rawText);
            data.put("twoStage", AiConfig.twoStageRecognize());
            data.put("preprocess", AiConfig.imagePreprocess());
            data.put("needReview", structureOnly);
            return ok(structureOnly
                    ? "已转写题目文字，但结构化未完成，请手动补充"
                    : "识别完成，请核对后保存", data);
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("识别失败：" + e.getMessage());
        }
    }

    /**
     * 联网校验：用检索到的参考资料核对识图结果是否可信。
     * <p>识别与校验拆成两次请求：识图先填表，校验随后单独跑，
     * 这样切换档位重新校验时不需要重新识图。校验失败不影响识别结果。</p>
     */
    public String verify() {
        try {
            currentUser();
            if (!AiConfig.searchEnabled()) {
                return fail("服务端已关闭联网校验（ai.search.enabled=false）");
            }
            String t = trim(title);
            String c = trim(content);
            if (isBlank(t) && isBlank(c)) {
                return fail("请先识别或填写题干后再校验");
            }
            String tier = "anysearch".equalsIgnoreCase(trim(searchTier)) ? "anysearch" : "default";
            String key = trim(anySearchKey);
            if (isBlank(key)) {
                // 前端没填时退回服务端兜底 Key（ai.properties）
                key = AiConfig.anySearchApiKey();
            }
            if ("anysearch".equals(tier) && isBlank(key)) {
                return fail("AnySearch 档需要填写 API Key，可在上传页设置里填写（仅保存在本机浏览器）");
            }

            long start = System.currentTimeMillis();
            String query = buildVerifyQuery(t, c);
            SearchService.Outcome outcome = SearchService.get().search(query, tier, key);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("tier", tier);
            detail.put("tierLabel", "anysearch".equals(tier) ? "AnySearch 档" : "默认档");
            detail.put("query", query);
            detail.put("engine", outcome.engine);
            detail.put("cached", outcome.cached);
            detail.put("searchElapsedMs", outcome.elapsedMs);
            detail.put("sources", sourceList(outcome.hits));

            if (outcome.hits.isEmpty()) {
                detail.put("status", "no_result");
                detail.put("level", "low");
                detail.put("levelLabel", "未校验");
                detail.put("confidence", 0);
                detail.put("reason", isBlank(outcome.error)
                        ? "没有检索到可参考的题目，识别结果请人工核对"
                        : "联网检索失败：" + outcome.error);
                detail.put("issues", new ArrayList<>());
                detail.put("suggestedAnswer", "");
                detail.put("suggestedCategory", "");
                detail.put("elapsedMs", System.currentTimeMillis() - start);
                return ok("未检索到可参考的题目", verifyData(detail));
            }

            String evidence = SearchService.get().evidence(outcome.hits, tier);
            QuestionVerifier.Verification v = new QuestionVerifier().verify(
                    trim(subjectName), typeLabel(type == null ? "" : type.trim()),
                    t, c, trim(options), trim(correctAnswer),
                    outcome.hits, evidence, outcome.engine);

            detail.put("status", v.status);
            detail.put("level", v.level);
            detail.put("levelLabel", verifyLevelLabel(v.level, v.status));
            detail.put("confidence", Math.round(v.confidence * 100) / 100.0);
            detail.put("reason", v.reason);
            detail.put("issues", v.issues);
            detail.put("suggestedAnswer", v.suggestedAnswer);
            detail.put("suggestedCategory", v.suggestedCategory);
            detail.put("model", v.model);
            detail.put("verifyElapsedMs", v.elapsedMs);
            detail.put("elapsedMs", System.currentTimeMillis() - start);
            return ok("ok".equals(v.status) ? "校验完成" : "校验未完成，识别结果不受影响", verifyData(detail));
        } catch (Exception e) {
            return fail("联网校验失败：" + e.getMessage());
        }
    }

    private Map<String, Object> verifyData(Map<String, Object> detail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verify", detail);
        return data;
    }

    private List<Map<String, Object>> sourceList(List<SearchHit> hits) {
        List<Map<String, Object>> list = new ArrayList<>();
        int index = 1;
        for (SearchHit h : hits) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index++);
            item.put("title", h.title);
            item.put("url", h.url);
            item.put("engine", h.engine);
            list.add(item);
        }
        return list;
    }

    private String buildVerifyQuery(String t, String c) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(t)) {
            sb.append(t.trim());
        }
        String body = c == null ? "" : c.trim().replaceAll("\\s+", " ");
        if (!body.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(body.length() > 120 ? body.substring(0, 120) : body);
        }
        // 学科词泛化（如“数学”），只在检索词过短时补到末尾，避免稀释题干特征
        if (!isBlank(subjectName) && sb.length() < 40) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(subjectName.trim());
        }
        String query = sb.toString().trim();
        return query.length() > 180 ? query.substring(0, 180) : query;
    }

    private String verifyLevelLabel(String level, String status) {
        if (!"ok".equals(status)) {
            return "未校验";
        }
        if ("high".equals(level)) {
            return "可信度高";
        }
        if ("low".equals(level)) {
            return "可信度低";
        }
        return "可信度中";
    }

    /** 单题错题分析：针对某道已录入的错题生成复盘建议 */
    public String analyze() {
        try {
            User user = currentUser();
            if (questionId == null) {
                return fail("请选择要分析的错题");
            }
            Question q = questionDao.findById(questionId, user.id);
            if (q == null) {
                return fail("题目不存在或无权限");
            }
            String prompt = buildAnalyzePrompt(q);
            AiService.Result result = aiService.chat(SYSTEM_ANALYZE, prompt,
                    () -> LocalAdvisor.explain(q));
            long logId = log(user.id, q.id, "analyze", prompt, result);
            Map<String, Object> data = body(result, logId, q.id);
            data.put("title", q.title);
            data.put("subjectName", q.subjectName);
            data.put("categoryName", q.categoryName);
            return ok(result.fallback ? "已生成本地规则分析" : "分析完成", data);
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("生成分析失败：" + e.getMessage());
        }
    }

    /** 学习总结：把错题本整体数据交给模型做阶段复盘 */
    public String summary() {
        try {
            User user = currentUser();
            long total = questionDao.countByUser(user.id);
            long today = questionDao.countToday(user.id);
            long uploads = questionDao.countUploads(user.id);
            List<Map<String, Object>> subjectStats = questionDao.countGroupBySubject(user.id);
            List<Map<String, Object>> topCategories = questionDao.countGroupByCategory(user.id, 5);
            List<Map<String, Object>> difficultyStats = questionDao.countGroupByDifficulty(user.id);
            List<Map<String, Object>> monthStats = questionDao.countGroupByMonth(user.id);
            List<Question> recent = questionDao.list(user.id, null, null, null, null, 1, 8).list;

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", total);
            stats.put("today", today);
            stats.put("uploads", uploads);
            stats.put("subjectStats", subjectStats);
            stats.put("topCategories", topCategories);
            stats.put("difficultyStats", difficultyStats);
            stats.put("monthStats", monthStats);
            stats.put("recentTitles", recent.stream().map(q -> q.title).limit(8).toArray());

            if (total == 0) {
                // 题都删光了，清掉旧总结，避免展示过期内容
                summaryDao.clear(user.id);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("stats", stats);
                data.put("text", "");
                data.put("fallback", true);
                return ok("错题本还是空的，先上传几道错题再来看总结", data);
            }

            String prompt = buildSummaryPrompt(total, today, subjectStats, topCategories,
                    difficultyStats, monthStats, recent);
            AiService.Result result = aiService.chat(SYSTEM_SUMMARY, prompt,
                    () -> LocalAdvisor.summary(total, today, subjectStats, topCategories, difficultyStats));
            long logId = log(user.id, null, "summary", prompt, result);
            saveSummary(user.id, result, stats, logId);

            Map<String, Object> data = body(result, logId, null);
            data.put("stats", stats);
            data.put("generated", true);
            return ok(result.fallback ? "已生成本地规则总结" : "总结生成成功", data);
        } catch (AiService.AiException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("生成总结失败：" + e.getMessage());
        }
    }

    /** 读取已保存的学习总结：进入页面时调用，不触发模型 */
    public String summaryLatest() {
        try {
            User user = currentUser();
            Map<String, Object> saved = summaryDao.findLatest(user.id);
            Map<String, Object> data = new LinkedHashMap<>();
            if (saved == null || saved.get("text") == null
                    || String.valueOf(saved.get("text")).trim().isEmpty()) {
                data.put("exists", false);
                return ok(data);
            }
            data.put("exists", true);
            data.put("text", saved.get("text"));
            data.put("provider", saved.get("provider"));
            data.put("providerLabel", saved.get("providerLabel"));
            data.put("model", saved.get("model"));
            data.put("elapsedMs", saved.get("elapsedMs"));
            data.put("fallback", saved.get("fallback"));
            data.put("logId", saved.get("logId"));
            data.put("updatedAt", saved.get("updatedAt"));
            data.put("updatedAtMillis", saved.get("updatedAtMillis"));
            data.put("stats", parseStatsJson((String) saved.get("statsJson")));
            return ok(data);
        } catch (Exception e) {
            return fail("读取学习总结失败：" + e.getMessage());
        }
    }

    /** 保存本次生成结果（覆盖旧的一份） */
    private void saveSummary(long userId, AiService.Result result, Map<String, Object> stats, long logId) {
        try {
            String statsJson = MAPPER.writeValueAsString(stats);
            summaryDao.save(userId, result.text, statsJson, result.provider, result.model,
                    label(result.provider), result.elapsedMs, result.fallback,
                    logId > 0 ? logId : null);
        } catch (Exception e) {
            System.err.println("[AI] 学习总结保存失败：" + e.getMessage());
        }
    }

    private Map<String, Object> parseStatsJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析前端传来的对话历史（只保留最近 max 条，且只接受 user / assistant 角色） */
    private List<Map<String, String>> parseHistory(String json, int max) {
        List<Map<String, String>> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JsonNode arr = MAPPER.readTree(json);
            if (!arr.isArray()) {
                return list;
            }
            for (JsonNode n : arr) {
                String role = n.path("role").asText("").trim();
                String content = n.path("content").asText("").trim();
                if (!("user".equals(role) || "assistant".equals(role)) || content.isEmpty()) {
                    continue;
                }
                if (content.length() > 2000) {
                    content = content.substring(0, 2000);
                }
                list.add(AiService.role(role, content));
            }
        } catch (Exception ignore) {
            // 历史格式异常时按新对话处理
        }
        if (list.size() > max) {
            list = new ArrayList<>(list.subList(list.size() - max, list.size()));
        }
        return list;
    }

    private String buildClassifyPrompt(String title, String content, List<Subject> subjects,
                                       List<Category> categories) {
        StringBuilder sb = new StringBuilder();
        sb.append("可选学科与知识点：\n");
        for (Subject s : subjects) {
            sb.append("- ").append(s.name).append("：");
            boolean first = true;
            for (Category c : categories) {
                if (c.subjectId != s.id) {
                    continue;
                }
                if (!first) {
                    sb.append("、");
                }
                sb.append(c.name);
                first = false;
            }
            if (first) {
                sb.append("（暂无知识点）");
            }
            sb.append("\n");
        }
        sb.append("\n题目标题：").append(title.isEmpty() ? "（未填写）" : title).append("\n");
        sb.append("题干内容：").append(content.isEmpty() ? "（未填写，只有图片）" : content).append("\n");
        sb.append("\n请只输出 JSON 对象。");
        return sb.toString();
    }

    private String buildRecognizePrompt(List<Subject> subjects, List<Category> categories) {
        StringBuilder sb = new StringBuilder();
        appendCatalog(sb, subjects, categories);
        sb.append("\n请识别这张图片里的错题，按约定的 JSON 结构输出。");
        return sb.toString();
    }

    /** 两段式识别的第二段：把图片转写文字结构化成录入字段 */
    private String buildStructurePrompt(List<Subject> subjects, List<Category> categories, String rawText) {
        StringBuilder sb = new StringBuilder();
        appendCatalog(sb, subjects, categories);
        String text = rawText == null ? "" : rawText.trim();
        if (text.length() > 6000) {
            text = text.substring(0, 6000);
        }
        sb.append("\n【图片转写】\n").append(text);
        sb.append("\n\n请按约定的 JSON 结构输出。");
        return sb.toString();
    }

    private void appendCatalog(StringBuilder sb, List<Subject> subjects, List<Category> categories) {
        if (subjects.isEmpty()) {
            sb.append("题库里还没有学科数据，请按通用学科名称（数学/语文/英语/物理/化学/生物/政治/历史/地理等）给出 subject。\n");
        } else {
            sb.append("可选学科与知识点：\n");
        }
        for (Subject s : subjects) {
            sb.append("- ").append(s.name).append("：");
            boolean first = true;
            for (Category c : categories) {
                if (c.subjectId != s.id) {
                    continue;
                }
                if (!first) {
                    sb.append("、");
                }
                sb.append(c.name);
                first = false;
            }
            if (first) {
                sb.append("（暂无知识点）");
            }
            sb.append("\n");
        }
    }

    /** 逐张（或逐条带）转写图片文字，单张失败自动重试一次 */
    private String ocrTiles(List<byte[]> tiles) {
        StringBuilder sb = new StringBuilder();
        for (byte[] tile : tiles) {
            String text = ocrOnce(tile);
            if (isBlank(text)) {
                text = ocrOnce(tile);
            }
            if (isBlank(text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(text.trim());
            if (sb.length() > 8000) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private String ocrOnce(byte[] tile) {
        try {
            AiService.Result r = aiService.chatWithImage(
                    SYSTEM_OCR, "请转写这张图片中的题目文字。", tile, null);
            return r.text;
        } catch (Exception e) {
            System.err.println("[AI] 图片转写失败：" + e.getMessage());
            return "";
        }
    }

    /** 结构化调用：失败不抛异常，交给上层决定是否用转写兜底 */
    private AiService.Result chatStructure(String prompt) {
        try {
            return aiService.chat(SYSTEM_STRUCTURE, prompt, false, null);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            return new AiService.Result(msg, "local", "local-rule", 0L, true);
        }
    }

    /** 结构化不可用时，用转写文本填一份可编辑的录入信息 */
    private VisionInfo fromRawText(String rawText, List<Subject> subjects, List<Category> categories) {
        String content = cut(rawText, 4000);
        String title = firstLine(content, 30);
        Match m = ruleMatch(title + " " + content, subjects, categories);
        return new VisionInfo(title, content, "essay", "", "", "", "", "", 2,
                m.subject, m.category,
                m.subject == null ? "" : m.subject.name,
                m.category == null ? "" : m.category.name);
    }

    private void logFail(long userId, String prompt, AiService.Result result) {
        try {
            aiDao.insert(userId, null, "recognize_fail", result.provider, result.model,
                    prompt, result.text, result.elapsedMs, false);
        } catch (Exception e) {
            System.err.println("[AI] 识别失败日志写入失败：" + e.getMessage());
        }
    }

    /** 失败提示里附带模型原始输出片段，便于定位是空返回还是格式问题 */
    private String preview(String text) {
        String s = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            return "（空）";
        }
        return s.length() <= 160 ? s : s.substring(0, 160) + "…";
    }


    /** 解析模型返回的识别结果 JSON */
    private VisionInfo parseVision(String text, List<Subject> subjects, List<Category> categories) {
        JsonNode n = extractJson(text);
        if (n == null) {
            return null;
        }
        String title = n.path("title").asText("").trim();
        String content = LatexNormalizer.normalize(n.path("content").asText("").trim());
        if (title.isEmpty() && content.isEmpty()) {
            return null;
        }
        if (title.isEmpty()) {
            title = firstLine(content, 30);
        }
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        if (content.length() > 4000) {
            content = content.substring(0, 4000);
        }
        String options = LatexNormalizer.normalize(parseVisionOptions(n.path("options")));
        int optionCount = optionList(options).size();
        String visionType = normalizeVisionType(n.path("type").asText(""), optionCount);
        String correctAnswer = LatexNormalizer.normalize(normalizeVisionAnswer(visionType,
                n.path("correctAnswer").asText("").trim(), optionCount));
        String answer = LatexNormalizer.normalize(cut(n.path("answer").asText("").trim(), 500));
        String analysis = LatexNormalizer.normalize(cut(n.path("analysis").asText("").trim(), 1000));
        String source = cut(n.path("source").asText("").trim(), 100);
        int difficulty = clampDifficulty(n.path("difficulty").asInt(2));
        String rawSubject = cut(n.path("subject").asText("").trim(), 20);
        String rawCategory = cut(n.path("category").asText("").trim(), 80);
        if ("null".equalsIgnoreCase(rawSubject)) {
            rawSubject = "";
        }
        if ("null".equalsIgnoreCase(rawCategory)) {
            rawCategory = "";
        }
        Subject subject = findSubject(rawSubject, subjects);
        Category category = subject == null ? null : findCategory(rawCategory, subject, categories);
        if (subject == null && rawSubject.isEmpty()) {
            // 模型确实没给出学科时，才用关键词规则兜底
            Match m = ruleMatch(title + " " + content, subjects, categories);
            subject = m.subject;
            category = m.category;
        }
        return new VisionInfo(title, content, visionType, options, correctAnswer, answer, analysis, source, difficulty,
                subject, category, rawSubject, rawCategory);
    }

    /** 识别结果里的题型，兼容中英文/别名；选择题没有选项时退化为解答题 */
    private String normalizeVisionType(String raw, int optionCount) {
        String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        String type;
        if (t.contains("选择") || t.contains("单选") || t.equals("choice")) {
            type = "choice";
        } else if (t.contains("判断")) {
            type = "judge";
        } else if (t.contains("填空")) {
            type = "blank";
        } else if (t.contains("阅读")) {
            type = "reading";
        } else if (t.contains("实验")) {
            type = "experiment";
        } else if (t.contains("写作") || t.contains("作文") || t.contains("书面表达") || t.equals("writing")) {
            type = "writing";
        } else if (t.contains("听力") || t.equals("listening")) {
            type = "listening";
        } else if (t.contains("翻译") || t.equals("translation")) {
            type = "translation";
        } else if (t.contains("计算") || t.equals("calculation")) {
            type = "calculation";
        } else if (t.contains("简答")) {
            type = "short";
        } else if (!t.isEmpty()) {
            type = "essay";
        } else {
            type = optionCount >= 2 ? "choice" : "essay";
        }
        if ("choice".equals(type) && optionCount < 2) {
            type = "essay";
        }
        return type;
    }

    /** 识图得到的选择题答案只保留合法选项字母，判断题统一为正确/错误 */
    private String normalizeVisionAnswer(String type, String raw, int optionCount) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if ("choice".equals(type)) {
            String key = s.substring(0, 1).toUpperCase(Locale.ROOT);
            int idx = key.charAt(0) - 'A';
            return (idx >= 0 && idx < optionCount) ? key : "";
        }
        if ("judge".equals(type)) {
            if (s.startsWith("正确") || s.startsWith("对") || s.startsWith("√") || s.equalsIgnoreCase("true")) {
                return "正确";
            }
            if (s.startsWith("错误") || s.startsWith("错") || s.startsWith("×") || s.equalsIgnoreCase("false")) {
                return "错误";
            }
            return "";
        }
        return cut(s, 2000);
    }

    /** 把模型给的选项统一成 ["A. 内容", ...] 的 JSON 数组 */
    private String parseVisionOptions(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "[]";
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isObject()) {
                    String key = item.path("key").asText("").trim();
                    String text = item.path("text").asText("").trim();
                    if (!text.isEmpty()) {
                        items.add(key.isEmpty() ? text : key + ". " + text);
                    }
                } else {
                    String v = item.asText("").trim();
                    if (!v.isEmpty()) {
                        items.add(v);
                    }
                }
            }
        } else if (node.isTextual()) {
            for (String line : node.asText("").split("\\r?\\n")) {
                String v = line.trim();
                if (!v.isEmpty()) {
                    items.add(v);
                }
            }
        }
        if (items.size() > 8) {
            items = new ArrayList<>(items.subList(0, 8));
        }
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String v = items.get(i);
            String letter = String.valueOf((char) ('A' + i));
            boolean hasKey = v.length() >= 2 && Character.isLetter(v.charAt(0))
                    && ".、:：)） ".indexOf(v.charAt(1)) >= 0;
            if (hasKey) {
                String rest = v.substring(1).replaceFirst("^[.、:：)）\\s]+", "").trim();
                normalized.add(letter + ". " + rest);
            } else {
                normalized.add(letter + ". " + v);
            }
        }
        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 选项 JSON 转列表，解析失败返回空列表 */
    private List<String> optionList(String json) {
        List<String> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String v = item.asText("").trim();
                    if (!v.isEmpty()) {
                        list.add(v);
                    }
                }
            }
        } catch (Exception ignore) {
            // 解析失败按没有选项处理
        }
        return list;
    }

    private String typeLabel(String type) {
        switch (type == null ? "" : type) {
            case "choice":
                return "选择题";
            case "judge":
                return "判断题";
            case "blank":
                return "填空题";
            case "short":
                return "简答题";
            case "reading":
                return "阅读理解";
            case "experiment":
                return "实验题";
            case "writing":
                return "写作题";
            case "listening":
                return "听力题";
            case "translation":
                return "翻译题";
            case "calculation":
                return "计算题";
            default:
                return "解答题";
        }
    }

    /** 从模型输出中截取第一个 JSON 对象并解析；兼容代码围栏、数组包裹与常见不规范写法 */
    private JsonNode extractJson(String text) {
        if (text == null) {
            return null;
        }
        String s = text.replace("```json", " ").replace("```", " ").trim();
        int from = s.indexOf('{');
        int to = s.lastIndexOf('}');
        if (from >= 0 && to > from) {
            JsonNode node = tryParseJson(s.substring(from, to + 1));
            if (node != null) {
                return node;
            }
        }
        // 少数模型会把对象包在数组里
        int arrFrom = s.indexOf('[');
        int arrTo = s.lastIndexOf(']');
        if (arrFrom >= 0 && arrTo > arrFrom) {
            JsonNode arr = tryParseJson(s.substring(arrFrom, arrTo + 1));
            if (arr != null && arr.isArray() && !arr.isEmpty() && arr.get(0).isObject()) {
                return arr.get(0);
            }
        }
        return null;
    }

    private JsonNode tryParseJson(String json) {
        String fixed = repairJsonEscapes(json);
        try {
            return MAPPER.readTree(fixed);
        } catch (Exception ignored) {
            // 再按宽松模式试一次（单引号、无引号字段、尾逗号）
            try {
                ObjectMapper lenient = new ObjectMapper();
                lenient.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
                lenient.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
                lenient.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
                lenient.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
                return lenient.readTree(fixed);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * 修复模型输出的 JSON 里没转义的 LaTeX 反斜杠：
     * 模型常把 \frac、\sqrt 直接写进 JSON 字符串，\f、\t、\b 会被当成转义符，
     * \√、\× 这类更是非法转义导致整段 JSON 解析失败。
     * 这里把已知 LaTeX 命令统一补成 \\，非法转义也补成 \\，保留 \n \t \" 等正常转义。
     */
    private String repairJsonEscapes(String json) {
        if (json == null || json.indexOf('\\') < 0) {
            return json;
        }
        StringBuilder out = new StringBuilder(json.length() + 64);
        int i = 0;
        int len = json.length();
        while (i < len) {
            char c = json.charAt(i);
            if (c != '\\' || i + 1 >= len) {
                out.append(c);
                i++;
                continue;
            }
            char next = json.charAt(i + 1);
            if (next == '\\' || next == '"' || next == '/') {
                out.append(c).append(next);
                i += 2;
                continue;
            }
            if (isAsciiLetter(next)) {
                int j = i + 1;
                while (j < len && isAsciiLetter(json.charAt(j))) {
                    j++;
                }
                String cmd = json.substring(i + 1, j);
                if (LATEX_COMMANDS.contains(cmd) || !isJsonEscapeStart(next)) {
                    out.append("\\\\").append(cmd);
                } else {
                    out.append(c).append(cmd);
                }
                i = j;
                continue;
            }
            if ("bfnrtu".indexOf(next) >= 0) {
                out.append(c).append(next);
            } else {
                // \√、\× 之类的非法转义
                out.append("\\\\").append(next);
            }
            i += 2;
        }
        return out.toString();
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isJsonEscapeStart(char c) {
        return "bfnrtu".indexOf(c) >= 0;
    }

    /** 常见 LaTeX 命令：模型写在 JSON 里时需要补成双反斜杠 */
    private static final java.util.Set<String> LATEX_COMMANDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "frac", "dfrac", "tfrac", "cfrac", "sqrt", "times", "cdot", "div", "pm", "mp",
            "leq", "geq", "neq", "approx", "equiv", "sim", "cong", "propto", "infty",
            "left", "right", "text", "mathrm", "mathbf", "overline", "underline", "hat", "bar",
            "vec", "overrightarrow", "begin", "end", "quad", "qquad", "partial", "nabla",
            "forall", "exists", "emptyset", "subset", "subseteq", "supset", "supseteq",
            "union", "perp", "parallel", "angle", "triangle", "because", "therefore",
            "cdots", "ldots", "dots", "alpha", "beta", "gamma", "delta", "theta", "lambda",
            "mu", "nu", "xi", "pi", "rho", "sigma", "tau", "phi", "chi", "psi", "omega",
            "Delta", "Gamma", "Theta", "Lambda", "Pi", "Sigma", "Phi", "Psi", "Omega",
            "sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lg", "lim", "sum",
            "prod", "int", "oint", "circ", "to", "in", "cup", "cap", "not", "mid", "colon",
            "leftrightarrow", "Rightarrow", "Leftarrow", "longrightarrow", "mapsto"));

    private String firstLine(String text, int max) {
        String s = text.replaceAll("\\s+", " ").trim();
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** 把题型、选项与标准答案拼进提示词，让讲解贴合题型 */
    private void appendTypeInfo(StringBuilder sb, Question q) {
        sb.append("题型：").append(typeLabel(q.type)).append("\n");
        List<String> opts = optionList(q.options);
        if ("choice".equals(q.type) && !opts.isEmpty()) {
            sb.append("选项：\n");
            for (String o : opts) {
                sb.append("  ").append(o).append("\n");
            }
        }
        if (q.correctAnswer != null && !q.correctAnswer.trim().isEmpty()) {
            sb.append("标准答案：").append(q.correctAnswer.trim()).append("\n");
        }
    }

    private String buildAnalyzePrompt(Question q) {
        String difficulty = q.difficulty == null ? "中等"
                : q.difficulty == 1 ? "简单" : q.difficulty == 3 ? "困难" : "中等";
        StringBuilder sb = new StringBuilder();
        sb.append("请分析这道错题。\n");
        sb.append("学科：").append(nvl(q.subjectName, "未填写")).append("\n");
        sb.append("知识点：").append(nvl(q.categoryName, "未分类")).append("\n");
        sb.append("难度：").append(difficulty).append("\n");
        appendTypeInfo(sb, q);
        sb.append("题目标题：").append(nvl(q.title, "未填写")).append("\n");
        if (q.content != null && !q.content.trim().isEmpty()) {
            sb.append("题干：").append(q.content.trim()).append("\n");
        } else {
            sb.append("题干：题目以图片形式保存，无文字题干，请结合学科与知识点分析这类题的典型失分点。\n");
        }
        sb.append("我的作答：").append(nvl(q.answer, "（未填写）")).append("\n");
        sb.append("我记录的错因：").append(nvl(q.analysis, "（未填写）")).append("\n");
        return sb.toString();
    }

    private String buildSummaryPrompt(long total, long today, List<Map<String, Object>> subjectStats,
                                      List<Map<String, Object>> topCategories,
                                      List<Map<String, Object>> difficultyStats,
                                      List<Map<String, Object>> monthStats, List<Question> recent) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是我的错题本数据，请做阶段总结。\n");
        sb.append("错题总数：").append(total).append("，今日新增：").append(today).append("\n");

        sb.append("各学科分布：");
        if (subjectStats == null || subjectStats.isEmpty()) {
            sb.append("暂无\n");
        } else {
            for (Map<String, Object> row : subjectStats) {
                long c = toLong(row.get("count"));
                if (c > 0) {
                    sb.append(row.get("name")).append(" ").append(c).append(" 道；");
                }
            }
            sb.append("\n");
        }

        sb.append("高频知识点：");
        if (topCategories == null || topCategories.isEmpty()) {
            sb.append("暂无\n");
        } else {
            for (Map<String, Object> row : topCategories) {
                sb.append(row.get("name")).append("（").append(row.get("count")).append(" 道）");
            }
            sb.append("\n");
        }

        sb.append("难度分布：");
        if (difficultyStats == null || difficultyStats.isEmpty()) {
            sb.append("暂无\n");
        } else {
            for (Map<String, Object> row : difficultyStats) {
                sb.append(row.get("name")).append(" ").append(row.get("count")).append(" 道；");
            }
            sb.append("\n");
        }

        sb.append("按月录入：");
        if (monthStats == null || monthStats.isEmpty()) {
            sb.append("暂无\n");
        } else {
            for (Map<String, Object> row : monthStats) {
                sb.append(row.get("name")).append(" ").append(row.get("count")).append(" 道；");
            }
            sb.append("\n");
        }

        sb.append("最近录入的题目：");
        if (recent == null || recent.isEmpty()) {
            sb.append("暂无\n");
        } else {
            for (Question q : recent) {
                sb.append("《").append(q.title).append("》");
            }
            sb.append("\n");
        }
        sb.append("\n请按约定结构输出总结。");
        return sb.toString();
    }

    private long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 图片识别结果 */
    private static class VisionInfo {
        final String title;
        final String content;
        final String type;
        final String options;
        final String correctAnswer;
        final String answer;
        final String analysis;
        final String source;
        final int difficulty;
        final Subject subject;
        final Category category;
        /** 模型返回的原始学科 / 知识点名称，用于匹配失败时询问是否创建 */
        final String rawSubject;
        final String rawCategory;

        VisionInfo(String title, String content, String type, String options, String correctAnswer,
                   String answer, String analysis, String source,
                   int difficulty, Subject subject, Category category,
                   String rawSubject, String rawCategory) {
            this.title = title;
            this.content = content;
            this.type = type;
            this.options = options;
            this.correctAnswer = correctAnswer;
            this.answer = answer;
            this.analysis = analysis;
            this.source = source;
            this.difficulty = difficulty;
            this.subject = subject;
            this.category = category;
            this.rawSubject = rawSubject == null ? "" : rawSubject;
            this.rawCategory = rawCategory == null ? "" : rawCategory;
        }
    }

    /** 从模型输出中截取 JSON 并匹配到真实的学科/分类记录 */
    private Match parseMatch(String text, List<Subject> subjects, List<Category> categories) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        int from = s.indexOf('{');
        int to = s.lastIndexOf('}');
        if (from < 0 || to <= from) {
            return null;
        }
        try {
            JsonNode n = MAPPER.readTree(s.substring(from, to + 1));
            String subjectName = n.path("subject").asText("").trim();
            String categoryName = n.path("category").asText("").trim();
            int difficulty = n.path("difficulty").asInt(2);
            String reason = n.path("reason").asText("").trim();
            if (reason.length() > 120) {
                reason = reason.substring(0, 120);
            }
            Subject subject = findSubject(subjectName, subjects);
            if (subject == null) {
                return null;
            }
            Category category = findCategory(categoryName, subject, categories);
            return new Match(subject, category, clampDifficulty(difficulty),
                    reason.isEmpty() ? "AI 依据题干判断" : reason);
        } catch (Exception e) {
            return null;
        }
    }

    private Subject findSubject(String name, List<Subject> subjects) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        if (n.isEmpty() || "null".equalsIgnoreCase(n)) {
            return null;
        }
        for (Subject s : subjects) {
            if (s.name.equals(n) || n.contains(s.name) || s.name.contains(n)) {
                return s;
            }
        }
        return null;
    }

    private Category findCategory(String name, Subject subject, List<Category> categories) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        if (n.isEmpty() || "null".equalsIgnoreCase(n)) {
            return null;
        }
        Category loose = null;
        for (Category c : categories) {
            if (c.subjectId != subject.id) {
                continue;
            }
            if (c.name.equals(n)) {
                return c;
            }
            if (loose == null && (n.contains(c.name) || c.name.contains(n))) {
                loose = c;
            }
        }
        return loose;
    }

    /** 关键词规则兜底：学科看关键词命中数，知识点看与文本的 2 字重叠度 */
    private Match ruleMatch(String text, List<Subject> subjects, List<Category> categories) {
        String lower = text.toLowerCase();
        Subject bestSubject = null;
        int bestScore = 0;
        for (Subject s : subjects) {
            int score = lower.contains(s.name.toLowerCase()) ? 2 : 0;
            String[] kws = SUBJECT_KEYWORDS.get(s.name);
            if (kws != null) {
                for (String k : kws) {
                    if (lower.contains(k.toLowerCase())) {
                        score++;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSubject = s;
            }
        }
        if (bestSubject == null) {
            return new Match(null, null, 2, "关键词不足，未能判断学科");
        }
        Category bestCategory = null;
        int bestCatScore = 0;
        for (Category c : categories) {
            if (c.subjectId != bestSubject.id) {
                continue;
            }
            int sc = overlap(c.name, text);
            if (sc > bestCatScore) {
                bestCatScore = sc;
                bestCategory = c;
            }
        }
        String reason = "关键词匹配：" + bestSubject.name
                + (bestCategory == null ? "（未匹配到知识点）" : " · " + bestCategory.name);
        return new Match(bestSubject, bestCategory, 2, reason);
    }

    private int overlap(String name, String text) {
        if (name == null || name.length() < 2 || text == null) {
            return 0;
        }
        int score = 0;
        for (int i = 0; i + 2 <= name.length(); i++) {
            if (text.contains(name.substring(i, i + 2))) {
                score++;
            }
        }
        return score;
    }

    private int clampDifficulty(int d) {
        return d < 1 ? 1 : (d > 3 ? 3 : d);
    }

    /** 分类结果 */
    private static class Match {
        final Subject subject;
        final Category category;
        final int difficulty;
        final String reason;

        Match(Subject subject, Category category, int difficulty, String reason) {
            this.subject = subject;
            this.category = category;
            this.difficulty = difficulty;
            this.reason = reason;
        }

        String describe() {
            if (subject == null) {
                return "未识别出学科";
            }
            return subject.name + (category == null ? "" : " · " + category.name) + "（难度 " + difficulty + "）";
        }
    }

    private String buildExplainPrompt(Question q) {
        String difficulty = q.difficulty == null ? "中等"
                : q.difficulty == 1 ? "简单" : q.difficulty == 3 ? "困难" : "中等";
        StringBuilder sb = new StringBuilder();
        sb.append("请讲解下面这道错题。\n");
        sb.append("学科：").append(nvl(q.subjectName, "未填写")).append("\n");
        sb.append("知识点：").append(nvl(q.categoryName, "未分类")).append("\n");
        sb.append("难度：").append(difficulty).append("\n");
        appendTypeInfo(sb, q);
        sb.append("题目标题：").append(nvl(q.title, "未填写")).append("\n");
        if (q.content != null && !q.content.trim().isEmpty()) {
            sb.append("题干：").append(q.content.trim()).append("\n");
        } else {
            sb.append("题干：题目以图片形式保存，无文字题干。请根据学科与知识点给出这类题的通用解题思路与易错点。\n");
        }
        sb.append("我的作答：").append(nvl(q.answer, "（未填写）")).append("\n");
        sb.append("我记录的错因：").append(nvl(q.analysis, "（未填写）")).append("\n\n");
        sb.append("请按以下结构输出：\n");
        sb.append("1. 考查知识点（1-2 句）\n");
        sb.append("2. 错因诊断（指出我的作答或思路可能存在的问题）\n");
        sb.append("3. 正确解法（分步骤说明）\n");
        sb.append("4. 同类题提醒（1 条可立刻执行的复习建议）\n");
        return sb.toString();
    }

    private String buildAdvicePrompt(long total, long today, List<Map<String, Object>> subjectStats,
                                     List<Map<String, Object>> topCategories) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是我的错题统计，请给出复习建议。\n");
        sb.append("错题总数：").append(total).append("，今日新增：").append(today).append("\n");
        sb.append("各学科分布：");
        if (subjectStats == null || subjectStats.isEmpty()) {
            sb.append("暂无数据\n");
        } else {
            for (Map<String, Object> row : subjectStats) {
                sb.append(row.get("name")).append(" ").append(row.get("count")).append(" 道；");
            }
            sb.append("\n");
        }
        sb.append("高频知识点：");
        if (topCategories == null || topCategories.isEmpty()) {
            sb.append("暂无数据\n\n");
        } else {
            for (Map<String, Object> row : topCategories) {
                sb.append(row.get("name")).append("（").append(row.get("count")).append(" 道）");
            }
            sb.append("\n\n");
        }
        sb.append("请输出：\n1. 本周复习计划（3 条，具体到学科/知识点/题量）\n2. 学习习惯建议（2 条）");
        return sb.toString();
    }

    private Map<String, Object> body(AiService.Result result, long logId, Long qid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", result.text);
        data.put("provider", result.provider);
        data.put("providerLabel", label(result.provider));
        data.put("model", result.model);
        data.put("elapsedMs", result.elapsedMs);
        data.put("fallback", result.fallback);
        data.put("logId", logId);
        data.put("questionId", qid);
        return data;
    }

    private long log(long userId, Long qid, String scene, String prompt, AiService.Result result) {
        try {
            return aiDao.insert(userId, qid, scene, result.provider, result.model,
                    prompt, result.text, result.elapsedMs, !result.fallback);
        } catch (Exception e) {
            System.err.println("[AI] 日志写入失败：" + e.getMessage());
            return 0L;
        }
    }

    private String label(String provider) {
        return AiConfig.providerLabel(provider);
    }

    private String nvl(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setHistory(String history) {
        this.history = history;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void setForce(String force) {
        this.force = force;
    }

    public void setSearchTier(String searchTier) {
        this.searchTier = searchTier;
    }

    public void setAnySearchKey(String anySearchKey) {
        this.anySearchKey = anySearchKey;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }
}
