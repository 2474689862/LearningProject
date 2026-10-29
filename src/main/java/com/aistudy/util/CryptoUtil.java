package com.aistudy.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置项加解密：AES/GCM，密文形如 enc:v1:base64(iv + 密文)。
 * <p>密钥来自环境变量 APP_SECRET（未设置时用固定演示密钥）。本项目是学习项目，
 * 目标只是「数据库被直接查看时看不到明文 Key」，不做专业的密钥管理。</p>
 */
public final class CryptoUtil {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final SecretKeySpec KEY = new SecretKeySpec(deriveKey(), "AES");

    private CryptoUtil() {
    }

    private static byte[] deriveKey() {
        String secret = System.getenv("APP_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            secret = System.getProperty("APP_SECRET");
        }
        if (secret == null || secret.trim().isEmpty()) {
            secret = "ai-study-demo-secret";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(("ai-study-settings:" + secret).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密密钥失败", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密失败（例如换了 APP_SECRET）时按空值处理，让调用方回落到配置文件 */
    public static String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] plain = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Settings] 解密失败（APP_SECRET 可能已变更），按空值处理");
            return "";
        }
    }

    /** 接口回传用的掩码，只露后 4 位 */
    public static String mask(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        return plain.length() <= 4 ? "****" : "****" + plain.substring(plain.length() - 4);
    }
}
