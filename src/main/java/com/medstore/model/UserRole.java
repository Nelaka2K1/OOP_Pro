package com.medstore.model;

public enum UserRole {
    CUSTOMER,
    PHARMACIST,
    ADMIN;

    public static UserRole parse(String raw) {
        return UserRole.valueOf(raw.trim().toUpperCase());
    }
}
