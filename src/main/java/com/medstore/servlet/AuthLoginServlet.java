package com.medstore.servlet;

import com.medstore.dao.UserDAO;
import com.medstore.model.User;
import com.medstore.util.JsonResponses;
import com.medstore.util.PasswordHasher;
import com.medstore.servlet.util.UserJson;
import com.medstore.util.SessionKeys;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

@WebServlet(name = "AuthLoginServlet", urlPatterns = "/api/login")
public class AuthLoginServlet extends HttpServlet {

    private final UserDAO users = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LoginBody body = JsonResponses.gson().fromJson(req.getReader(), LoginBody.class);
        if (body == null || body.email == null || body.password == null) {
            JsonResponses.error(resp, 400, "email and password required");
            return;
        }
        User user;
        try {
            user = users.findByEmail(body.email.strip()).orElse(null);
        } catch (RuntimeException e) {
            getServletContext().log("Login lookup failed", e);
            JsonResponses.error(resp, 503,
                    "Database schema may be outdated — restart the app server, or run sql/migration_v2.sql");
            return;
        }
        if (user == null || !PasswordHasher.verify(body.password, user.getPasswordHash())) {
            JsonResponses.error(resp, 401, "invalid credentials");
            return;
        }
        HttpSession s = req.getSession(true);
        s.setAttribute(SessionKeys.USER_ID, user.getId());
        s.setAttribute(SessionKeys.ROLE, user.getRole().name());
        JsonResponses.writeJson(resp, 200, Map.of("user", UserJson.toMap(user)));
    }

    private static final class LoginBody {
        String email;
        String password;
    }
}
