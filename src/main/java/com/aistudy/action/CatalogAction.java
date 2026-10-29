package com.aistudy.action;

import com.aistudy.dao.CatalogDao;
import com.aistudy.dao.QuestionDao;
import com.aistudy.model.Category;
import com.aistudy.model.Subject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学科与题目分类（知识点）管理。
 * 学科支持自由创建：新增知识点时若填写的学科不存在，可一并创建。
 */
public class CatalogAction extends BaseAction {

    private final CatalogDao catalogDao = new CatalogDao();
    private final QuestionDao questionDao = new QuestionDao();

    private Integer subjectId;
    private Integer parentId;
    private Integer id;
    private String name;
    private String subjectName;
    private String categoryName;
    private String description;
    private String keyword;

    /** 学科列表 */
    public String subjects() {
        try {
            return ok(catalogDao.listSubjects());
        } catch (Exception e) {
            return fail("加载学科失败：" + e.getMessage());
        }
    }

    /** 分类平铺列表（可按学科、关键字过滤） */
    public String list() {
        try {
            return ok(catalogDao.listCategories(subjectId, keyword));
        } catch (Exception e) {
            return fail("加载分类失败：" + e.getMessage());
        }
    }

    /** 学科 + 分类树，供上传页级联选择 / 分类管理页展示 */
    public String tree() {
        try {
            List<Subject> subjects = catalogDao.listSubjects();
            List<Category> categories = catalogDao.listCategories(null, null);

            Map<Integer, Map<String, Object>> subjectNodes = new LinkedHashMap<>();
            Map<Integer, Map<String, Object>> categoryNodes = new LinkedHashMap<>();

            for (Subject s : subjects) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", s.id);
                node.put("name", s.name);
                node.put("code", s.code);
                node.put("questionCount", s.questionCount);
                node.put("categoryCount", s.categoryCount);
                node.put("categories", new ArrayList<Map<String, Object>>());
                subjectNodes.put(s.id, node);
            }
            for (Category c : categories) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", c.id);
                node.put("name", c.name);
                node.put("subjectId", c.subjectId);
                node.put("subjectName", c.subjectName);
                node.put("parentId", c.parentId);
                node.put("description", c.description);
                node.put("questionCount", c.questionCount);
                node.put("children", new ArrayList<Map<String, Object>>());
                categoryNodes.put(c.id, node);
            }
            // 挂载父子关系
            for (Category c : categories) {
                Map<String, Object> node = categoryNodes.get(c.id);
                if (c.parentId != null && c.parentId > 0 && categoryNodes.containsKey(c.parentId)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children =
                            (List<Map<String, Object>>) categoryNodes.get(c.parentId).get("children");
                    children.add(node);
                } else if (subjectNodes.containsKey(c.subjectId)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> roots =
                            (List<Map<String, Object>>) subjectNodes.get(c.subjectId).get("categories");
                    roots.add(node);
                }
            }
            return ok(new ArrayList<>(subjectNodes.values()));
        } catch (Exception e) {
            return fail("加载分类树失败：" + e.getMessage());
        }
    }

    /** 新增分类 */
    public String create() {
        try {
            if (subjectId == null || subjectId <= 0) {
                return fail("请选择学科");
            }
            if (isBlank(name)) {
                return fail("请输入分类名称");
            }
            if (name.trim().length() > 80) {
                return fail("分类名称过长");
            }
            if (!catalogDao.subjectExists(subjectId)) {
                return fail("学科不存在");
            }
            if (catalogDao.categoryExists(subjectId, name.trim())) {
                return fail("该学科下已存在同名分类");
            }
            int pid = (parentId == null || parentId < 0) ? 0 : parentId;
            long newId = catalogDao.insertCategory(subjectId, pid, name.trim(), trim(description));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", newId);
            data.put("subjectId", subjectId);
            data.put("parentId", pid);
            data.put("name", name.trim());
            return ok("分类创建成功", data);
        } catch (Exception e) {
            return fail("创建分类失败：" + e.getMessage());
        }
    }

    /** 新增学科（名称自由输入） */
    public String createSubject() {
        try {
            String n = trim(name);
            if (isBlank(n)) {
                return fail("请输入学科名称");
            }
            if (n.length() > 20) {
                return fail("学科名称不能超过 20 个字");
            }
            Subject exists = catalogDao.findSubjectByName(n);
            if (exists != null) {
                Map<String, Object> data = subjectMap(exists);
                data.put("existed", true);
                return ok("学科已存在，已直接选中", data);
            }
            String code = catalogDao.buildSubjectCode(n);
            int newId = catalogDao.insertSubject(n, code, 99);
            if (newId <= 0) {
                return fail("创建学科失败");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", newId);
            data.put("name", n);
            data.put("code", code);
            data.put("existed", false);
            return ok("学科「" + n + "」创建成功", data);
        } catch (Exception e) {
            return fail("创建学科失败：" + e.getMessage());
        }
    }

    /** 重命名学科 */
    public String updateSubject() {
        try {
            if (id == null) {
                return fail("参数错误");
            }
            if (!catalogDao.subjectExists(id)) {
                return fail("学科不存在");
            }
            String n = trim(name);
            if (isBlank(n)) {
                return fail("请输入学科名称");
            }
            if (n.length() > 20) {
                return fail("学科名称不能超过 20 个字");
            }
            Subject same = catalogDao.findSubjectByName(n);
            if (same != null && !same.id.equals(id)) {
                return fail("已存在同名学科「" + same.name + "」");
            }
            int rows = catalogDao.updateSubjectName(id, n);
            return rows > 0 ? ok("学科已重命名", null) : fail("学科不存在");
        } catch (Exception e) {
            return fail("重命名失败：" + e.getMessage());
        }
    }

    /** 删除学科（下面还有知识点或题目时禁止） */
    public String deleteSubject() {
        try {
            if (id == null) {
                return fail("参数错误");
            }
            long cats = catalogDao.countCategoriesBySubject(id);
            if (cats > 0) {
                return fail("该学科下还有 " + cats + " 个知识点，请先删除知识点");
            }
            long used = catalogDao.countQuestionsBySubject(id);
            if (used > 0) {
                return fail("该学科下还有 " + used + " 道题目，无法删除");
            }
            int rows = catalogDao.deleteSubject(id);
            return rows > 0 ? ok("学科已删除", null) : fail("学科不存在");
        } catch (Exception e) {
            return fail("删除学科失败：" + e.getMessage());
        }
    }

    /** 编辑知识点：改名称 / 说明 / 上级分类 */
    public String updateCategory() {
        try {
            if (id == null) {
                return fail("参数错误");
            }
            Category old = catalogDao.findCategoryById(id);
            if (old == null) {
                return fail("知识点不存在");
            }
            String n = trim(name);
            if (isBlank(n)) {
                return fail("请输入分类名称");
            }
            if (n.length() > 80) {
                return fail("分类名称过长");
            }
            if (catalogDao.categoryExistsExcept(old.subjectId, n, id)) {
                return fail("该学科下已存在同名分类");
            }
            Integer pid = parentId;
            if (pid != null && pid == id) {
                return fail("上级分类不能是自己");
            }
            if (pid != null && pid > 0) {
                Category parent = catalogDao.findCategoryById(pid);
                if (parent == null) {
                    return fail("上级分类不存在");
                }
                if (!parent.subjectId.equals(old.subjectId)) {
                    return fail("上级分类必须属于同一学科");
                }
            }
            int rows = catalogDao.updateCategory(id, n, trim(description), pid);
            return rows > 0 ? ok("知识点已更新", null) : fail("知识点不存在");
        } catch (Exception e) {
            return fail("更新失败：" + e.getMessage());
        }
    }

    /**
     * 一键创建：学科不存在则创建，知识点不存在则创建。
     * 供识图后确认「创建识别到的学科」使用。
     */
    public String quickCreate() {
        try {
            String sn = trim(subjectName);
            if (isBlank(sn)) {
                return fail("请提供学科名称");
            }
            if (sn.length() > 20) {
                sn = sn.substring(0, 20);
            }
            boolean createdSubject = false;
            Subject subject = catalogDao.findSubjectByName(sn);
            if (subject == null) {
                String code = catalogDao.buildSubjectCode(sn);
                int newId = catalogDao.insertSubject(sn, code, 99);
                if (newId <= 0) {
                    return fail("创建学科失败");
                }
                subject = new Subject();
                subject.id = newId;
                subject.name = sn;
                subject.code = code;
                createdSubject = true;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("subjectId", subject.id);
            data.put("subjectName", subject.name);
            data.put("createdSubject", createdSubject);

            String cn = trim(categoryName);
            if (!isBlank(cn)) {
                if (cn.length() > 80) {
                    cn = cn.substring(0, 80);
                }
                Category cat = findCategoryByName(subject.id, cn);
                boolean createdCategory = false;
                if (cat == null) {
                    long cid = catalogDao.insertCategory(subject.id, 0, cn, trim(description));
                    data.put("categoryId", cid);
                    data.put("categoryName", cn);
                    createdCategory = true;
                } else {
                    data.put("categoryId", cat.id);
                    data.put("categoryName", cat.name);
                }
                data.put("createdCategory", createdCategory);
            }
            String msg = createdSubject ? "已创建学科「" + subject.name + "」" : "已使用现有学科「" + subject.name + "」";
            return ok(msg, data);
        } catch (Exception e) {
            return fail("创建失败：" + e.getMessage());
        }
    }

    /** 删除分类（分类下有题目时禁止删除） */
    public String delete() {
        try {
            if (id == null) {
                return fail("参数错误");
            }
            long used = questionDao.countByCategory(id);
            if (used > 0) {
                return fail("该分类下还有 " + used + " 道题目，无法删除");
            }
            int rows = catalogDao.deleteCategory(id);
            return rows > 0 ? ok("已删除", null) : fail("分类不存在");
        } catch (Exception e) {
            return fail("删除分类失败：" + e.getMessage());
        }
    }

    private Category findCategoryByName(int subjectId, String name) {
        for (Category c : catalogDao.listCategories(subjectId, null)) {
            if (c.name != null && c.name.equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    private Map<String, Object> subjectMap(Subject s) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", s.id);
        data.put("name", s.name);
        data.put("code", s.code);
        return data;
    }

    public void setSubjectId(Integer subjectId) {
        this.subjectId = subjectId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
