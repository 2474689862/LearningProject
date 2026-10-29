package com.aistudy.config;

import com.aistudy.dao.SettingDao;
import com.aistudy.util.CryptoUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设置页写入的全局配置覆盖层。
 * <p>优先级：环境变量 &gt; 本类（t_setting）&gt; ai.properties &gt; 代码默认值。
 * 值保存在内存里，保存后立即生效，不需要重启 Tomcat；进程启动时由
 * {@code AppInitListener} 调一次 {@link #reload()}。</p>
 */
public final class AppSettings {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /** 需要加密落库的键：接口只回掩码，不回明文 */
    private static final Set<String> SECRET_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("ai.cloud.apiKey")));

    private static final SettingDao DAO = new SettingDao();

    private AppSettings() {
    }

    /** 从数据库重新加载；表不存在或数据库不可用时保持空覆盖层（等同于只用 ai.properties） */
    public static void reload() {
        try {
            Map<String, String> loaded = DAO.loadAll();
            CACHE.clear();
            CACHE.putAll(loaded);
            if (!loaded.isEmpty()) {
                System.out.println("[AI-Study] 已加载 " + loaded.size() + " 项页面设置");
            }
        } catch (Exception e) {
            System.err.println("[AI-Study] 加载页面设置失败（忽略，继续用配置文件）：" + e.getMessage());
        }
    }

    /** 取页面设置的原始值（敏感项已解密）；未设置或为空返回 null */
    public static String get(String key) {
        String raw = CACHE.get(key);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return SECRET_KEYS.contains(key) ? CryptoUtil.decrypt(raw) : raw;
    }

    /**
     * 批量保存。值为空字符串表示删除该项（回落到 ai.properties）。
     *
     * @param values 键值对，调用方需先做白名单校验
     */
    public static void save(Map<String, String> values, Long userId) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (value.isEmpty()) {
                DAO.delete(key);
                CACHE.remove(key);
                continue;
            }
            String stored = SECRET_KEYS.contains(key) ? CryptoUtil.encrypt(value) : value;
            DAO.upsert(key, stored, userId);
            CACHE.put(key, stored);
        }
    }

    /** 清空所有页面设置，回到纯 ai.properties 行为 */
    public static void reset() {
        DAO.deleteAll();
        CACHE.clear();
    }

    /** 当前被页面覆盖的键（不含敏感值本身） */
    public static Set<String> overriddenKeys() {
        return new HashSet<>(CACHE.keySet());
    }

    /** 敏感项的掩码，供接口展示 */
    public static String masked(String key) {
        String plain = get(key);
        return plain == null ? "" : CryptoUtil.mask(plain);
    }

    /** 组装「恢复默认」用的键值快照，仅供诊断展示 */
    public static Map<String, String> snapshot() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : CACHE.keySet()) {
            map.put(key, SECRET_KEYS.contains(key) ? masked(key) : CACHE.get(key));
        }
        return map;
    }
}
