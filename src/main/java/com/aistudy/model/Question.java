package com.aistudy.model;

import java.util.Date;

public class Question {

    public Long id;
    public Long userId;
    public Integer subjectId;
    public String subjectName;
    public Integer categoryId;
    public String categoryName;
    public String title;
    public String content;
    /** 题型：choice 选择 / judge 判断 / blank 填空 / short 简答 / essay 解答 */
    public String type;
    /** 选择题选项，JSON 数组字符串，如 ["A. 1","B. 2"]；其他题型为 null */
    public String options;
    /** 标准答案：选择题为选项字母，判断题为正确/错误，其余为参考答案 */
    public String correctAnswer;
    public String imagePath;
    public String answer;
    public String analysis;
    public String source;
    public Integer difficulty;
    public Integer status;
    public Date createdAt;
    public Date updatedAt;
}
