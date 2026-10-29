package com.aistudy.ai;

import com.aistudy.model.Question;

import java.util.List;
import java.util.Map;

/**
 * 本地规则兜底：未配置任何模型时，用模板 + 简单统计给出可用建议，
 * 保证 Demo 在无网络、无 Key 的环境下也能演示 AI 板块。
 */
public final class LocalAdvisor {

    private LocalAdvisor() {
    }

    public static String explain(Question q) {
        String subject = nvl(q.subjectName, "该学科");
        String category = nvl(q.categoryName, "核心知识点");
        String difficulty = q.difficulty == null ? "中等"
                : q.difficulty == 1 ? "简单" : q.difficulty == 3 ? "困难" : "中等";
        String answer = q.answer == null || q.answer.trim().isEmpty() ? "（未填写作答）" : q.answer.trim();
        String wrong = q.analysis == null || q.analysis.trim().isEmpty() ? "（未填写错因）" : q.analysis.trim();

        StringBuilder sb = new StringBuilder();
        sb.append("【考查知识点】\n")
                .append(subject).append(" · ").append(category).append("，难度：").append(difficulty).append("\n\n");
        sb.append("【错因诊断】\n")
                .append("你的作答：").append(answer).append("\n")
                .append("你记录的错因：").append(wrong).append("\n")
                .append("建议按顺序自查：\n")
                .append("1. 题目条件是否读全，是否漏掉定义域、取值范围、单位等限制；\n")
                .append("2. 关键公式 / 定理的适用前提是否满足；\n")
                .append("3. 计算过程中是否出现符号、移项、分配律等低级失误；\n")
                .append("4. 是否只写了结论，缺少必要的推理步骤导致检查不出错误。\n\n");
        sb.append("【解题思路框架】\n")
                .append("1. 先把已知条件与所求目标翻译成「").append(category).append("」中的标准表达；\n")
                .append("2. 选择可直接套用的定理或公式，写出适用条件；\n")
                .append("3. 分步推导，每一步只做一次变形，便于回溯；\n")
                .append("4. 用特殊值 / 图像 / 量纲做一次结果校验。\n\n");
        sb.append("【复习建议】\n")
                .append("回到课本「").append(category).append("」章节的例题，重做 2 道同类题；")
                .append("把本题的错因浓缩成一句话写进错题本，下次复习只看这句话。\n\n");
        sb.append("提示：以上内容由本地规则模板生成。配置 DeepSeek API Key 或启动本机 Ollama（qwen2.5vl）后，可获得真正的模型讲解。");
        return sb.toString();
    }

    public static String advice(long total, long today, List<Map<String, Object>> subjectStats,
                                List<Map<String, Object>> topCategories) {
        StringBuilder sb = new StringBuilder();
        String topSubject = null;
        long topCount = 0;
        if (subjectStats != null) {
            for (Map<String, Object> row : subjectStats) {
                long c = toLong(row.get("count"));
                if (c > topCount) {
                    topCount = c;
                    topSubject = String.valueOf(row.get("name"));
                }
            }
        }
        String weak = topSubject == null ? "暂无明显薄弱学科" : topSubject + "（" + topCount + " 道）";
        String topCat = "暂无";
        if (topCategories != null && !topCategories.isEmpty()) {
            Map<String, Object> first = topCategories.get(0);
            topCat = first.get("name") + "（" + toLong(first.get("count")) + " 道）";
        }

        sb.append("【当前状态】\n")
                .append("累计错题 ").append(total).append(" 道，今日新增 ").append(today).append(" 道；")
                .append("薄弱学科：").append(weak).append("；高频知识点：").append(topCat).append("。\n\n");
        sb.append("【本周复习计划】\n")
                .append("1. 先攻薄弱学科：挑选 ").append(topSubject == null ? "任意学科" : topSubject)
                .append(" 的错题，每天重做 2 道，覆盖不同知识点；\n")
                .append("2. 二次复盘：只看错因不看答案，能独立复述解题第一步再算过关；\n")
                .append("3. 交叉复习：每 3 天把已过关的题目随机抽 1 道重做，防止遗忘。\n\n");
        sb.append("【习惯建议】\n")
                .append("· 录入错题时至少写一句「我为什么会错」，这比抄答案更有价值；\n")
                .append("· 每周固定一次 20 分钟错题回顾，比考前突击更有效。\n\n");
        sb.append("提示：以上内容由本地规则模板生成。配置 DeepSeek API Key 或启动本机 Ollama 后，可获得更具体的个性化建议。");
        return sb.toString();
    }

    /**
     * 学习总结的本地兜底：把统计数据整理成结构化文案，保证模型不可用时页面仍有内容。
     */
    public static String summary(long total, long today, List<Map<String, Object>> subjectStats,
                                 List<Map<String, Object>> topCategories,
                                 List<Map<String, Object>> difficultyStats) {
        StringBuilder sb = new StringBuilder();
        StringBuilder weak = new StringBuilder();
        StringBuilder rest = new StringBuilder();
        String topSubject = null;
        long topCount = 0;
        if (subjectStats != null) {
            for (Map<String, Object> row : subjectStats) {
                long c = toLong(row.get("count"));
                if (c <= 0) {
                    continue;
                }
                if (c > topCount) {
                    topCount = c;
                    topSubject = String.valueOf(row.get("name"));
                }
                if (weak.length() > 0) {
                    weak.append("、");
                }
                weak.append(row.get("name")).append(" ").append(c).append(" 道");
            }
        }
        if (topCategories != null) {
            for (Map<String, Object> row : topCategories) {
                if (rest.length() > 0) {
                    rest.append("；");
                }
                rest.append(row.get("name")).append("（").append(toLong(row.get("count"))).append(" 道）");
            }
        }
        String diff = buildDifficultyText(difficultyStats);

        sb.append("【整体判断】\n")
                .append("累计错题 ").append(total).append(" 道，今日新增 ").append(today).append(" 道；")
                .append(subjectStats == null || subjectStats.isEmpty() ? "暂无明显学科分布。" : "学科分布：" + weak + "。")
                .append("\n\n");
        sb.append("【薄弱环节】\n");
        if (topSubject == null) {
            sb.append("数据还不够，先持续录入错题再判断薄弱学科。\n\n");
        } else {
            sb.append("1. 优先补 ").append(topSubject).append("（").append(topCount).append(" 道，占比最高）；\n")
                    .append("2. 高频知识点：").append(rest.length() == 0 ? "暂无标注" : rest.toString()).append("；\n")
                    .append("3. 从这些知识点里各挑 2 道错题重做，能独立讲清思路后再做新题。\n\n");
        }
        sb.append("【错因共性】\n").append(diff).append("\n\n");
        sb.append("【下周计划】\n")
                .append("1. ").append(topSubject == null ? "任选一个学科" : topSubject)
                .append("：每天 2 道错题重做 + 1 句话错因；\n")
                .append("2. 高频知识点：每两天挑 1 个，做 3 道同类题巩固；\n")
                .append("3. 周末用 20 分钟只看错因回顾整周内容。\n\n");
        sb.append("提示：以上内容由本地规则模板生成。启动本机 Ollama 视觉模型后可获得更具体的 AI 总结。");
        return sb.toString();
    }

    private static String buildDifficultyText(List<Map<String, Object>> difficultyStats) {
        if (difficultyStats == null || difficultyStats.isEmpty()) {
            return "暂无难度数据。";
        }
        long hard = 0;
        long total = 0;
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : difficultyStats) {
            long c = toLong(row.get("count"));
            total += c;
            if ("困难".equals(row.get("name"))) {
                hard = c;
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(row.get("name")).append(" ").append(c).append(" 道");
        }
        String judge = total > 0 && hard * 100 / total >= 40
                ? "困难题占比偏高，说明基础题型掌握不牢或审题不细，建议先回归课本例题。"
                : "难度分布较均衡，主要失分更可能来自计算与审题细节。";
        return sb.append("；").append(judge).toString();
    }

    public static String ask(String question) {
        return "当前运行在本地规则模式，无法回答开放式问题。\n\n"
                + "你问的是：「" + question + "」\n\n"
                + "启用真实模型的两个办法：\n"
                + "1. 在 src/main/resources/ai.properties 中填写 ai.deepseek.apiKey（DeepSeek 官方 API）；\n"
                + "2. 本机 Ollama 已安装在 F:\\Tools\\Ollama（模型目录 F:\\Tools\\AI_MODEL），执行 ollama pull qwen2.5vl:7b 后把 ai.provider 设为 ollama。\n"
                + "配置完成后重启服务即可。";
    }

    private static long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String nvl(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }
}
