package com.medstore.servlet;

import com.medstore.dao.UserDAO;
import com.medstore.model.UserRole;
import com.medstore.util.Config;
import com.medstore.util.JsonResponses;
import com.medstore.util.PasswordHasher;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "AuthRegisterServlet", urlPatterns = "/api/register")
public class AuthRegisterServlet extends HttpServlet {

    private final UserDAO users = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RegisterBody body = parseBody(req);
        if (body == null || body.email == null || body.password == null || body.fullName == null || body.role == null) {
            JsonResponses.error(resp, 400, "email, password, fullName, role required");
            return;
        }
        UserRole role;
        try {
            role = UserRole.parse(body.role);
        } catch (Exception e) {
            JsonResponses.error(resp, 400, "invalid role");
            return;
        }
        if (role == UserRole.ADMIN) {
            String key = Config.get("security.admin_registration_key", "").trim();
            String provided = body.adminKey != null ? body.adminKey.trim() : "";
            if (key.isEmpty() || !key.equals(provided)) {
                JsonResponses.error(resp, 403, "admin registration key required");
                return;
            }
        }
        if (!body.email.contains("@")) {
            JsonResponses.error(resp, 400, "invalid email");
            return;
        }
        if (body.password.length() < 6) {
            JsonResponses.error(resp, 400, "password must be at least 6 characters");
            return;
        }
        if (users.findByEmail(body.email.strip()).isPresent()) {
            JsonResponses.error(resp, 409, "email already registered");
            return;
        }
        try {
            int id = users.insertRegistered(body.email.strip(),
                    PasswordHasher.hash(body.password), body.fullName.strip(), role);
            JsonResponses.writeJson(resp, 201, Map.of(
                    "id", id,
                    "email", body.email.strip(),
                    "fullName", body.fullName.strip(),
                    "role", role.name()));
        } catch (RuntimeException db) {
            if (db.getCause() instanceof java.sql.SQLException se) {
                if (se.getErrorCode() == 1062) {
                    JsonResponses.error(resp, 409, "email already registered");
                    return;
                }
                if (se.getErrorCode() == 1265) {
                    JsonResponses.error(resp, 503,
                            "Database schema outdated — restart the app server to apply updates, "
                                    + "or run sql/migration_v2.sql");
                    return;
                }
            }
            throw db;
        }
    }

    private static RegisterBody parseBody(HttpServletRequest req) throws IOException {
        return JsonResponses.gson().fromJson(req.getReader(), RegisterBody.class);
    }

    private static final class RegisterBody {
        String email;
        String password;
        String fullName;
        String role;
        String adminKey;
    }

}
