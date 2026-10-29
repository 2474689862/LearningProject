package com.aistudy.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 密码工具：SHA-256(salt + 明文)，注册时生成随机盐。
 */
public final class PasswordUtil {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String newSalt() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return hex(bytes);
    }

    public static String hash(String rawPassword, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((salt + rawPassword).getBytes(StandardCharsets.UTF_8));
            return hex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("密码加密失败", e);
        }
    }

    /** 固定盐值，用于演示账号 */
    public static final String DEMO_SALT = "a1b2c3d4e5f60718";

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX[(b >> 4) & 0x0f]).append(HEX[b & 0x0f]);
        }
        return sb.toString();
    }
}
