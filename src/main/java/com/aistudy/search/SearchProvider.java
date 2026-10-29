package com.aistudy.search;

import java.util.List;

/**
 * 检索提供方：默认档（网页检索）与 AnySearch 档（REST 接口）各自实现。
 */
public interface SearchProvider {

    /** 展示给前端的引擎名 */
    String name();

    /**
     * 执行检索。
     *
     * @param query  检索词
     * @param apiKey 仅 AnySearch 需要，其他档位忽略
     */
    List<SearchHit> search(String query, String apiKey) throws Exception;
}
