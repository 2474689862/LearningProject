package com.aistudy.action;

import com.aistudy.dao.UserDao;
import com.aistudy.model.User;
import com.aistudy.util.JsonUtil;
import com.aistudy.util.PasswordUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 登录 / 注册 / 退出 / 当前用户。
 */
public class AuthAction extends BaseAction {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private final UserDao userDao = new UserDao();

    private String username;
    private String password;
    private String nickname;
    private String oldPassword;
    private String newPassword;

    public String login() {
        try {
            if (isBlank(username) || isBlank(password)) {
                return fail("请输入账号和密码");
            }
            User user = userDao.findByUsername(username.trim());
            if (user == null || !PasswordUtil.hash(password, user.salt).equals(user.password)) {
                return fail("账号或密码错误");
            }
            if (user.status != null && user.status == 0) {
                return fail("账号已被禁用");
            }
            user.password = null;
            user.salt = null;
            session().put(SESSION_USER, user);
            return ok("登录成功", brief(user));
        } catch (Exception e) {
            return fail("登录失败：" + e.getMessage());
        }
    }

    public String register() {
        try {
            if (isBlank(username) || isBlank(password)) {
                return fail("请输入账号和密码");
            }
            String name = username.trim();
            if (!USERNAME_PATTERN.matcher(name).matches()) {
                return fail("账号需为 3-20 位字母、数字或下划线");
            }
            if (password.length() < 6) {
                return fail("密码至少 6 位");
            }
            if (userDao.findByUsername(name) != null) {
                return fail("该账号已存在");
            }
            User user = new User();
            user.username = name;
            user.salt = PasswordUtil.newSalt();
            user.password = PasswordUtil.hash(password, user.salt);
            user.nickname = isBlank(nickname) ? name : nickname.trim();
            user.role = "STUDENT";
            userDao.insert(user);

            user.password = null;
            user.salt = null;
            session().put(SESSION_USER, user);
            return ok("注册成功", brief(user));
        } catch (Exception e) {
            return fail("注册失败：" + e.getMessage());
        }
    }

    public String logout() {
        session().clear();
        return ok("已退出登录", null);
    }

    public String me() {
        User user = currentUser();
        if (user == null) {
            writeJson(JsonUtil.result(401, "未登录", null));
            return null;
        }
        return ok(brief(user));
    }

    /** 修改昵称（设置页 → 账号与数据） */
    public String updateProfile() {
        User user = currentUser();
        if (user == null) {
            writeJson(JsonUtil.result(401, "未登录", null));
            return null;
        }
        try {
            if (isBlank(nickname)) {
                return fail("昵称不能为空");
            }
            String name = nickname.trim();
            if (name.length() > 50) {
                return fail("昵称不能超过 50 个字");
            }
            userDao.updateNickname(user.id, name);
            user.nickname = name;
            return ok("昵称已更新", brief(user));
        } catch (Exception e) {
            return fail("更新失败：" + e.getMessage());
        }
    }

    /** 修改密码（设置页 → 账号与数据） */
    public String changePassword() {
        User user = currentUser();
        if (user == null) {
            writeJson(JsonUtil.result(401, "未登录", null));
            return null;
        }
        try {
            if (isBlank(oldPassword) || isBlank(newPassword)) {
                return fail("请填写原密码和新密码");
            }
            if (newPassword.length() < 6) {
                return fail("新密码至少 6 位");
            }
            User db = userDao.findByUsername(user.username);
            if (db == null || !PasswordUtil.hash(oldPassword, db.salt).equals(db.password)) {
                return fail("原密码不正确");
            }
            String salt = PasswordUtil.newSalt();
            userDao.updatePassword(user.id, PasswordUtil.hash(newPassword, salt), salt);
            return ok("密码已修改，下次登录请使用新密码", null);
        } catch (Exception e) {
            return fail("修改密码失败：" + e.getMessage());
        }
    }

    private Map<String, Object> brief(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.id);
        map.put("username", user.username);
        map.put("nickname", user.nickname);
        map.put("role", user.role);
        map.put("createdAt", user.createdAt);
        return map;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
