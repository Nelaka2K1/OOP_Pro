package com.medstore.servlet.util;

import com.medstore.model.User;
import com.medstore.servlet.UploadServlet;

import java.util.HashMap;
import java.util.Map;

public final class UserJson {

    private UserJson() {
    }

    public static Map<String, Object> toMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("role", user.getRole().name());
        map.put("profileImagePath", user.getProfileImagePath());
        if (user.getProfileImagePath() != null && !user.getProfileImagePath().isBlank()) {
            map.put("profileImageUrl", UploadServlet.publicUrl(user.getProfileImagePath()));
        }
        return map;
    }
}
