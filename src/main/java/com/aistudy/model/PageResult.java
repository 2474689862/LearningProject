package com.aistudy.model;

import java.util.List;

public class PageResult<T> {

    public long total;
    public int page;
    public int size;
    public List<T> list;

    public PageResult(long total, int page, int size, List<T> list) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.list = list;
    }
}
