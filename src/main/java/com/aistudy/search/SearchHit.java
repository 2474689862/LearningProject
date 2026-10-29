package com.aistudy.search;

/**
 * 一条联网检索结果。
 * content 仅 AnySearch 档会直接返回清洗后的正文，默认档通常为空。
 */
public class SearchHit {

    public final String title;
    public final String url;
    public final String snippet;
    public final String content;
    public final String engine;

    public SearchHit(String title, String url, String snippet, String content, String engine) {
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.snippet = snippet == null ? "" : snippet;
        this.content = content == null ? "" : content;
        this.engine = engine == null ? "" : engine;
    }
}
