package com.aistudy.search;

import com.aistudy.ai.AiConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 检索调度：按档位选择提供方，做结果去重/截断、短期缓存与证据块拼装。
 * <p>相同题干重复识别时直接命中缓存，避免反复访问搜索引擎。</p>
 */
public class SearchService {

    private static final SearchService INSTANCE = new SearchService();

    public static SearchService get() {
        return INSTANCE;
    }

    /** 检索结果 + 过程信息，供 Action 组装响应 */
    public static class Outcome {
        public final List<SearchHit> hits;
        public final String tier;
        public final String engine;
        public final boolean cached;
        public final long elapsedMs;
        public final String error;

        Outcome(List<SearchHit> hits, String tier, String engine,
                boolean cached, long elapsedMs, String error) {
            this.hits = hits;
            this.tier = tier;
            this.engine = engine;
            this.cached = cached;
            this.elapsedMs = elapsedMs;
            this.error = error;
        }
    }

    private static final class CacheItem {
        final Outcome outcome;
        final long at;

        CacheItem(Outcome outcome, long at) {
            this.outcome = outcome;
            this.at = at;
        }
    }

    private final Map<String, CacheItem> cache = new LinkedHashMap<String, CacheItem>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheItem> eldest) {
            return size() > AiConfig.searchCacheSize();
        }
    };

    /**
     * @param tier default / anysearch
     */
    public Outcome search(String query, String tier, String apiKey) {
        String t = "anysearch".equalsIgnoreCase(tier == null ? "" : tier.trim()) ? "anysearch" : "default";
        String cacheKey = t + "|" + (query == null ? "" : query.trim().toLowerCase(Locale.ROOT));

        synchronized (cache) {
            CacheItem entry = cache.get(cacheKey);
            if (entry != null) {
                if (System.currentTimeMillis() - entry.at <= AiConfig.searchCacheTtlMs()) {
                    Outcome old = entry.outcome;
                    return new Outcome(old.hits, t, old.engine, true, 0L, null);
                }
                cache.remove(cacheKey);
            }
        }

        long start = System.currentTimeMillis();
        SearchProvider provider = "anysearch".equals(t)
                ? new AnySearchProvider()
                : new DefaultSearchProvider();
        try {
            List<SearchHit> hits = normalize(provider.search(query, apiKey));
            Outcome outcome = new Outcome(hits, t, engines(hits, provider.name()), false,
                    System.currentTimeMillis() - start, null);
            if (!hits.isEmpty()) {
                synchronized (cache) {
                    cache.put(cacheKey, new CacheItem(outcome, System.currentTimeMillis()));
                }
            }
            return outcome;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            return new Outcome(Collections.<SearchHit>emptyList(), t, provider.name(), false,
                    System.currentTimeMillis() - start, msg);
        }
    }

    /** 按实际命中的引擎生成展示名，例如“Bing”“Bing + 搜狗”；无结果时回落提供方名称 */
    private static String engines(List<SearchHit> hits, String fallback) {
        if (hits == null || hits.isEmpty()) {
            return fallback;
        }
        Set<String> names = new LinkedHashSet<>();
        for (SearchHit h : hits) {
            if (h.engine != null && !h.engine.trim().isEmpty()) {
                names.add(h.engine.trim());
            }
        }
        return names.isEmpty() ? fallback : String.join(" + ", names);
    }

    private List<SearchHit> normalize(List<SearchHit> raw) {
        List<SearchHit> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (raw != null) {
            for (SearchHit h : raw) {
                if (h.url == null || !h.url.startsWith("http")) {
                    continue;
                }
                String key = h.url.replaceAll("/+$", "").toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    continue;
                }
                out.add(h);
                if (out.size() >= AiConfig.searchTopN()) {
                    break;
                }
            }
        }
        return out;
    }

    /** 拼成带编号的证据块，塞进校验提示词 */
    public String evidence(List<SearchHit> hits, String tier) {
        int maxChars = AiConfig.searchMaxChars(tier);
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (SearchHit h : hits) {
            String body = "anysearch".equals(tier)
                    ? firstNonEmpty(h.content, h.snippet)
                    : firstNonEmpty(h.snippet, h.content);
            String block = "[" + index + "] " + h.title + "\n" + h.url + "\n" + cut(body, 360) + "\n\n";
            int remain = maxChars - sb.length();
            if (remain <= 0) {
                break;
            }
            if (block.length() > remain) {
                sb.append(block, 0, remain);
                break;
            }
            sb.append(block);
            index++;
        }
        return sb.toString().trim();
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
