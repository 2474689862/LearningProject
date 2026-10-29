package com.aistudy.interceptor;

import com.aistudy.action.BaseAction;
import com.aistudy.util.JsonUtil;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionInvocation;
import org.apache.struts2.interceptor.AbstractInterceptor;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Map;

/**
 * 登录校验拦截器：未登录直接返回 401 JSON，不进入 Action。
 */
public class LoginInterceptor extends AbstractInterceptor {

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        Map<String, Object> session = ActionContext.getContext().getSession();
        Object user = session.get(BaseAction.SESSION_USER);
        if (user != null) {
            return invocation.invoke();
        }
        HttpServletResponse response = ServletActionContext.getResponse();
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.write(JsonUtil.toJson(JsonUtil.result(401, "登录已过期，请重新登录", null)));
            out.flush();
        }
        return null;
    }
}
