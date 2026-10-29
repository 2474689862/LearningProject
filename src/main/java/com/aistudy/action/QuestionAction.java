package com.aistudy.action;

import com.aistudy.dao.CatalogDao;
import com.aistudy.dao.QuestionDao;
import com.aistudy.model.Category;
import com.aistudy.model.Question;
import com.aistudy.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 错题（题目）录入、列表、详情、删除与统计。
 * 题型覆盖高中常见形式：选择 / 判断 / 填空 / 简答 / 解答 / 阅读 / 实验 / 写作 / 听力 / 翻译 / 计算，
 * 不同题型用不同模板填写，但统一落到 题干、选项、标准答案、我的作答 四个字段。
 */
public class QuestionAction extends BaseAction {

    /** 支持的题型（顺序即展示顺序） */
    private static final List<String> TYPES = Arrays.asList(
            "choice", "judge", "blank", "short", "essay",
            "reading", "experiment", "writing", "listening", "translation", "calculation");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final QuestionDao questionDao = new QuestionDao();
    private final CatalogDao catalogDao = new CatalogDao();

    private Long id;
    private Integer subjectId;
    private Integer categoryId;
    private String title;
    private String content;
    private String type;
    private String options;
    private String correctAnswer;
    private String imagePath;
    private String answer;
    private String analysis;
    private String source;
    private Integer difficulty;
    private String keyword;
    private Integer page;
    private Integer size;

    /** 题目分页列表 */
    public String list() {
        try {
            User user = currentUser();
            int p = (page == null || page < 1) ? 1 : page;
            int s = (size == null || size < 1) ? 10 : Math.min(size, 50);
            return ok(questionDao.list(user.id, subjectId, categoryId, keyword, type, p, s));
        } catch (Exception e) {
            return fail("加载题目失败：" + e.getMessage());
        }
    }

    /** 题目详情 */
    public String detail() {
        try {
            if (id == null) {
                return fail("参数错误");
            }
            Question q = questionDao.findById(id, currentUser().id);
            return q == null ? fail("题目不存在") : ok(q);
        } catch (Exception e) {
            return fail("加载详情失败：" + e.getMessage());
        }
    }

    /** 新增题目（可绑定已上传图片） */
    public String create() {
        try {
            User user = currentUser();
            if (subjectId == null || subjectId <= 0) {
                return fail("请选择学科");
            }
            if (isBlank(title)) {
                return fail("请输入题目标题");
            }
            if (title.trim().length() > 200) {
                return fail("题目标题不能超过 200 字");
            }
            if (!catalogDao.subjectExists(subjectId)) {
                return fail("学科不存在");
            }

            Question q = new Question();
            q.userId = user.id;
            q.subjectId = subjectId;
            q.categoryId = (categoryId == null || categoryId <= 0) ? null : categoryId;
            q.title = title.trim();
            q.content = trim(content);
            q.type = normalizeType(type);
            q.options = normalizeOptions(q.type, options);
            q.correctAnswer = normalizeCorrectAnswer(q.type, q.options, correctAnswer);
            q.imagePath = trim(imagePath);
            q.answer = trim(answer);
            q.analysis = trim(analysis);
            q.source = trim(source);
            q.difficulty = (difficulty == null || difficulty < 1 || difficulty > 3) ? 2 : difficulty;

            long newId = questionDao.insert(q);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", newId);
            return ok("题目已保存", data);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("保存题目失败：" + e.getMessage());
        }
    }

    /** 编辑已有错题（只允许改自己的题目，图片不变） */
    public String update() {
        try {
            User user = currentUser();
            if (id == null) {
                return fail("参数错误");
            }
            Question old = questionDao.findById(id, user.id);
            if (old == null) {
                return fail("题目不存在或无权限");
            }
            if (subjectId == null || subjectId <= 0) {
                return fail("请选择学科");
            }
            if (isBlank(title)) {
                return fail("请输入题目标题");
            }
            if (title.trim().length() > 200) {
                return fail("题目标题不能超过 200 字");
            }
            if (!catalogDao.subjectExists(subjectId)) {
                return fail("学科不存在");
            }
            Integer cid = (categoryId == null || categoryId <= 0) ? null : categoryId;
            if (cid != null) {
                Category cat = catalogDao.findCategoryById(cid);
                if (cat == null) {
                    return fail("知识点不存在");
                }
                if (!cat.subjectId.equals(subjectId)) {
                    return fail("知识点与学科不匹配");
                }
            }

            Question q = new Question();
            q.id = old.id;
            q.subjectId = subjectId;
            q.categoryId = cid;
            q.title = title.trim();
            q.content = trim(content);
            q.type = normalizeType(type);
            q.options = normalizeOptions(q.type, options);
            q.correctAnswer = normalizeCorrectAnswer(q.type, q.options, correctAnswer);
            q.answer = trim(answer);
            q.analysis = trim(analysis);
            q.source = trim(source);
            q.difficulty = (difficulty == null || difficulty < 1 || difficulty > 3) ? 2 : difficulty;

            int rows = questionDao.update(q, user.id);
            if (rows <= 0) {
                return fail("题目不存在或无权限");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", q.id);
            return ok("题目已更新", data);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("更新题目失败：" + e.getMessage());
        }
    }

    /** 删除（逻辑删除） */
    public String delete() {
        try {
            if (id == null) {
                return fail("参数错误");
            }
            int rows = questionDao.softDelete(id, currentUser().id);
            return rows > 0 ? ok("已删除", null) : fail("题目不存在或无权限");
        } catch (Exception e) {
            return fail("删除失败：" + e.getMessage());
        }
    }

    /** 首页统计 */
    public String stats() {
        try {
            User user = currentUser();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalQuestions", questionDao.countByUser(user.id));
            data.put("todayQuestions", questionDao.countToday(user.id));
            data.put("totalUploads", questionDao.countUploads(user.id));
            data.put("totalCategories", catalogDao.listCategories(null, null).size());
            data.put("subjectStats", questionDao.countGroupBySubject(user.id));
            return ok(data);
        } catch (Exception e) {
            return fail("统计失败：" + e.getMessage());
        }
    }

    /* ---------- 题型相关规整 ---------- */

    /** 题型白名单，未知值回退为解答题（老数据也按这个处理） */
    private String normalizeType(String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return TYPES.contains(t) ? t : "essay";
    }

    /** 只有选择题保留选项：JSON 数组、2-8 项；其他题型一律清空 */
    private String normalizeOptions(String qType, String raw) {
        if (!"choice".equals(qType)) {
            return null;
        }
        String s = raw == null ? "" : raw.trim();
        List<String> items = new ArrayList<>();
        if (!s.isEmpty()) {
            JsonNode node = null;
            try {
                node = MAPPER.readTree(s);
            } catch (Exception ignore) {
                node = null;
            }
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    String v = item.asText("").trim();
                    if (!v.isEmpty()) {
                        items.add(cut(v, 200));
                    }
                }
            } else {
                // 容错：也接受「[A. 1, B. 2]」或每行一个选项的写法
                String body = s;
                boolean bracketed = body.startsWith("[") && body.endsWith("]") && body.length() > 1;
                if (bracketed) {
                    body = body.substring(1, body.length() - 1);
                }
                String[] parts = bracketed ? body.split("\\s*,\\s*") : body.split("\\r?\\n");
                for (String part : parts) {
                    String v = part.trim().replaceAll("^\"|\"$", "").trim();
                    if (!v.isEmpty()) {
                        items.add(cut(v, 200));
                    }
                }
            }
        }
        if (items.size() > 8) {
            items = new ArrayList<>(items.subList(0, 8));
        }
        if (items.size() < 2) {
            throw new IllegalArgumentException("选择题至少需要 2 个选项");
        }
        try {
            return MAPPER.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalArgumentException("选项格式不正确");
        }
    }

    /** 标准答案按题型校验：选择题是选项字母，判断题是正确/错误，其余存文本 */
    private String normalizeCorrectAnswer(String qType, String optionsJson, String raw) {
        String s = raw == null ? "" : raw.trim();
        if ("choice".equals(qType)) {
            if (s.isEmpty()) {
                return null;
            }
            String key = s.substring(0, 1).toUpperCase(Locale.ROOT);
            List<String> opts = parseOptions(optionsJson);
            int idx = key.charAt(0) - 'A';
            if (idx < 0 || idx >= opts.size()) {
                throw new IllegalArgumentException("标准答案必须是已填写的选项之一");
            }
            return key;
        }
        if ("judge".equals(qType)) {
            if (s.isEmpty()) {
                return null;
            }
            if ("正确".equals(s) || "对".equals(s) || "√".equals(s)) {
                return "正确";
            }
            if ("错误".equals(s) || "错".equals(s) || "×".equals(s)) {
                return "错误";
            }
            throw new IllegalArgumentException("判断题标准答案只能是「正确」或「错误」");
        }
        return s.isEmpty() ? null : cut(s, 2000);
    }

    private List<String> parseOptions(String optionsJson) {
        List<String> list = new ArrayList<>();
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return list;
        }
        try {
            JsonNode node = MAPPER.readTree(optionsJson);
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    list.add(item.asText(""));
                }
            }
        } catch (Exception ignore) {
            // 解析失败视为没有选项，交给上层报错
        }
        return list;
    }

    private String cut(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSubjectId(Integer subjectId) {
        this.subjectId = subjectId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
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

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
