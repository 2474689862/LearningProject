package com.aistudy.action;

import com.aistudy.model.User;
import com.aistudy.util.JsonUtil;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Map;

/**
 * 所有 Action 的基类：统一 JSON 响应 + 会话读取。
 */
public abstract class BaseAction extends ActionSupport {

    public static final String SESSION_USER = "loginUser";

    protected Map<String, Object> session() {
        return ActionContext.getContext().getSession();
    }

    protected User currentUser() {
        Object u = session().get(SESSION_USER);
        return u instanceof User ? (User) u : null;
    }

    protected void writeJson(Object body) {
        HttpServletResponse response = ServletActionContext.getResponse();
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try (PrintWriter out = response.getWriter()) {
            out.write(JsonUtil.toJson(body));
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException("响应写出失败", e);
        }
    }

    protected String ok(Object data) {
        writeJson(JsonUtil.ok(data));
        return null;
    }

    protected String ok(String msg, Object data) {
        writeJson(JsonUtil.ok(msg, data));
        return null;
    }

    protected String fail(String msg) {
        writeJson(JsonUtil.fail(msg));
        return null;
    }

    protected String trim(String value) {
        return value == null ? null : value.trim();
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
