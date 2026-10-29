package com.aistudy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

public class User {

    public Long id;
    public String username;
    @JsonIgnore
    public String password;
    @JsonIgnore
    public String salt;
    public String nickname;
    public String role;
    public Integer status;
    public Date createdAt;
}
