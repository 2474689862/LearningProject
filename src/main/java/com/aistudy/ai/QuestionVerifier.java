package com.aistudy.ai;

import com.aistudy.search.SearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 联网校验：把「图片识别结果」和「检索证据」一起交给文字模型，
 * 让它核对识别是否可信并给出修正建议。
 * <p>只做提示，不直接改表单数据；检索失败或模型异常时返回 unavailable，
 * 识别结果照常可用。</p>
 */
public class QuestionVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM =
            "你是错题录入校验员。学生会先给你图片识别出的题目信息，再给你联网检索到的参考资料。\n"
                    + "你的任务是核对识别结果是否可信，并给出修正建议。\n"
                    + "规则：\n"
                    + "1. 只依据给出的识别结果与参考资料判断，不要编造资料里没有的条件；\n"
                    + "2. 参考资料不一定是同一道题：只能用来纠正错别字、术语、公式写法，或补充标准答案；"
                    + "明显不是同一题时不要硬套答案；\n"
                    + "3. issues 只列真实存在的问题：ocr（错字/公式识别错误）、condition（题干条件缺失）、"
                    + "type（题型判断错误）、answer（标准答案缺失或与选项不符）；没有就给空数组；\n"
                    + "4. confidence 取 0-1 之间的小数：0.75 以上=high，0.45-0.75=medium，0.45 以下=low；"
                    + "不能因为「看起来通顺」就给高分；\n"
                    + "5. 只输出一个 JSON 对象，不要解释、不要 Markdown 代码块；\n"
                    + "6. 输出格式：{\"confidence\":0.8,\"level\":\"high\","
                    + "\"issues\":[{\"type\":\"ocr\",\"detail\":\"…\"}],"
                    + "\"suggestedAnswer\":\"\",\"suggestedCategory\":\"\","
                    + "\"cited\":[1,2],\"reason\":\"一句话说明判断依据\"}";

    private final AiService ai = new AiService();

    /** 校验结果，字段与前端校验面板一一对应 */
    public static class Verification {
        public String status = "unavailable";
        public double confidence;
        public String level = "medium";
        public String reason = "";
        public String suggestedAnswer = "";
        public String suggestedCategory = "";
        public String model = "";
        public long elapsedMs;
        public List<Map<String, String>> issues = new ArrayList<>();
    }

    public Verification verify(String subjectName, String typeLabel, String title, String content,
                               String options, String correctAnswer, List<SearchHit> hits,
                               String evidence, String engine) {
        Verification v = new Verification();
        long start = System.currentTimeMillis();
        String prompt = buildPrompt(subjectName, typeLabel, title, content, options,
                correctAnswer, hits, evidence);
        try {
            AiService.Result result = ai.chat(SYSTEM, prompt, false, null);
            v.model = result.model;
            v.elapsedMs = System.currentTimeMillis() - start;
            JsonNode node = parseJson(result.text);
            if (node == null) {
                v.reason = "模型没有返回可解析的校验结果，识别内容不受影响";
                return v;
            }
            v.status = "ok";
            double confidence = node.path("confidence").asDouble(0.5);
            if (confidence < 0) {
                confidence = 0.5;
            }
            v.confidence = Math.min(1.0, Math.max(0.0, confidence));
            String level = node.path("level").asText("").trim().toLowerCase(Locale.ROOT);
            v.level = ("high".equals(level) || "medium".equals(level) || "low".equals(level))
                    ? level : levelOf(v.confidence);
            v.reason = cut(node.path("reason").asText(""), 200);
            v.suggestedAnswer = cut(node.path("suggestedAnswer").asText(""), 800);
            v.suggestedCategory = cut(node.path("suggestedCategory").asText(""), 60);
            v.issues = parseIssues(node.path("issues"));
            return v;
        } catch (Exception e) {
            v.elapsedMs = System.currentTimeMillis() - start;
            v.reason = "校验模型调用失败：" + (e.getMessage() == null ? e.toString() : e.getMessage());
            return v;
        }
    }

    private String buildPrompt(String subjectName, String typeLabel, String title, String content,
                               String options, String correctAnswer, List<SearchHit> hits,
                               String evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("【图片识别结果】\n");
        sb.append("学科：").append(blank(subjectName)).append("\n");
        sb.append("题型：").append(blank(typeLabel)).append("\n");
        sb.append("标题：").append(blank(title)).append("\n");
        sb.append("题干：").append(cut(blank(content), 1200)).append("\n");
        if (!blank(options).isEmpty()) {
            sb.append("选项：").append(cut(blank(options), 400)).append("\n");
        }
        sb.append("识别出的标准答案：").append(blank(correctAnswer)).append("\n\n");
        sb.append("【联网检索参考资料】（编号对应 cited）\n");
        sb.append(evidence.isEmpty() ? "（无可用资料）" : evidence).append("\n\n");
        sb.append("请核对上述识别结果，按约定 JSON 输出。本次共检索到 ")
                .append(hits == null ? 0 : hits.size()).append(" 条资料。");
        return sb.toString();
    }

    private static JsonNode parseJson(String text) {
        if (text == null) {
            return null;
        }
        String s = text.replace("```json", "").replace("```", "").trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return MAPPER.readTree(s.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Map<String, String>> parseIssues(JsonNode issues) {
        List<Map<String, String>> out = new ArrayList<>();
        if (issues == null || !issues.isArray()) {
            return out;
        }
        for (JsonNode item : issues) {
            String detail;
            String type;
            if (item.isObject()) {
                type = item.path("type").asText("").trim();
                detail = item.path("detail").asText("").trim();
            } else {
                type = "";
                detail = item.asText("").trim();
            }
            if (detail.isEmpty()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("type", type);
            row.put("typeLabel", issueLabel(type));
            row.put("detail", cut(detail, 160));
            out.add(row);
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    private static String issueLabel(String type) {
        switch (type == null ? "" : type.trim().toLowerCase(Locale.ROOT)) {
            case "ocr":
                return "识别错字";
            case "condition":
                return "条件缺失";
            case "type":
                return "题型存疑";
            case "answer":
                return "答案存疑";
            default:
                return "待核对";
        }
    }

    private static String levelOf(double confidence) {
        if (confidence >= 0.75) {
            return "high";
        }
        return confidence >= 0.45 ? "medium" : "low";
    }

    private static String blank(String value) {
        if (value == null) {
            return "";
        }
        String t = value.trim();
        return t.isEmpty() ? "（未填写）" : t;
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
