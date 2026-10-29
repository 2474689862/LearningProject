package com.aistudy.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Category {

    public Integer id;
    public Integer subjectId;
    public String subjectName;
    public Integer parentId;
    public String name;
    public String description;
    public Integer sortNo;
    public long questionCount;
    public List<Category> children = new ArrayList<>();
}
