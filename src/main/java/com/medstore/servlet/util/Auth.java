package com.medstore.servlet.util;

import com.medstore.model.UserRole;
import com.medstore.util.SessionKeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Optional;

public final class Auth {

    private Auth() {
    }

    public static Integer userId(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) {
            return null;
        }
        Object v = s.getAttribute(SessionKeys.USER_ID);
        return v instanceof Integer i ? i : null;
    }

    public static Optional<UserRole> role(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) {
            return Optional.empty();
        }
        Object v = s.getAttribute(SessionKeys.ROLE);
        if (v instanceof String r) {
            try {
                return Optional.of(UserRole.valueOf(r));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
