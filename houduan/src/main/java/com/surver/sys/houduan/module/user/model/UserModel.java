package com.surver.sys.houduan.module.user.model;

public class UserModel {

    private Long id;
    private String username;
    private String realName;
    private String role;
    private String status;
    private String createdAt;
    private boolean localAccount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isLocalAccount() {
        return localAccount;
    }

    public void setLocalAccount(boolean localAccount) {
        this.localAccount = localAccount;
    }
}
