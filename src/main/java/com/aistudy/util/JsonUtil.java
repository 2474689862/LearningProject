package com.aistudy.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 输出工具（统一响应格式：code / msg / data）。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"code\":500,\"msg\":\"JSON序列化失败\"}";
        }
    }

    public static Map<String, Object> result(int code, String msg, Object data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("msg", msg);
        map.put("data", data);
        return map;
    }

    public static Map<String, Object> ok(Object data) {
        return result(0, "ok", data);
    }

    public static Map<String, Object> ok(String msg, Object data) {
        return result(0, msg, data);
    }

    public static Map<String, Object> fail(String msg) {
        return result(1, msg, null);
    }
}
