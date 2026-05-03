package com.medstore.model;

/** DTO exposed to clients (encapsulates safe fields only). */
public final class UserPublicView {
    private final Integer id;
    private final String email;
    private final String fullName;
    private final String role;

    public UserPublicView(Integer id, String email, String fullName, String role) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }
}
