package com.aistudy.search;

import com.aistudy.ai.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AnySearch 档：调用 REST 搜索接口，结果自带清洗后的正文，无需二次抓取。
 */
public class AnySearchProvider implements SearchProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "AnySearch";
    }

    @Override
    public List<SearchHit> search(String query, String apiKey) throws Exception {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("AnySearch 档需要填写 API Key");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("max_results", Math.min(Math.max(1, AiConfig.searchTopN()), 10));
        payload.put("language", "zh-CN");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(4000))
                .build();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(AiConfig.anySearchBase() + "/search"))
                .timeout(Duration.ofMillis(AiConfig.searchTimeoutMs("anysearch")))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res;
        try {
            res = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("AnySearch 响应超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AnySearch 请求被中断");
        } catch (Exception e) {
            throw new IllegalStateException("AnySearch 请求失败：" + e.getMessage());
        }

        int status = res.statusCode();
        if (status == 401 || status == 403) {
            throw new IllegalStateException("AnySearch Key 无效或无权限（HTTP " + status + "）");
        }
        if (status == 429) {
            throw new IllegalStateException("AnySearch 请求过于频繁（HTTP 429），请稍后再试");
        }
        if (status != 200) {
            throw new IllegalStateException("AnySearch HTTP " + status + "：" + cut(res.body()));
        }

        JsonNode root = MAPPER.readTree(res.body());
        int code = root.path("code").asInt(0);
        if (code != 0) {
            String message = root.path("message").asText("");
            throw new IllegalStateException("AnySearch 返回错误：" + (message.isEmpty() ? "code=" + code : message));
        }

        JsonNode results = root.path("data").path("results");
        List<SearchHit> hits = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode item : results) {
                String url = item.path("url").asText("").trim();
                if (url.isEmpty()) {
                    continue;
                }
                hits.add(new SearchHit(
                        item.path("title").asText("").trim(),
                        url,
                        item.path("snippet").asText("").trim(),
                        item.path("content").asText("").trim(),
                        "AnySearch"));
            }
        }
        return hits;
    }

    private static String cut(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "…";
    }
}
