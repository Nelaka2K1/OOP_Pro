package com.medstore.servlet;

import com.medstore.dao.UserDAO;
import com.medstore.model.User;
import com.medstore.servlet.util.Auth;
import com.medstore.util.JsonResponses;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "ProfileServlet", urlPatterns = "/api/profile")
public class ProfileServlet extends HttpServlet {

    private final UserDAO users = new UserDAO();

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        UpdateBody body = JsonResponses.gson().fromJson(req.getReader(), UpdateBody.class);
        if (body == null || body.email == null || body.fullName == null) {
            JsonResponses.error(resp, 400, "email and fullName required");
            return;
        }
        if (!body.email.contains("@")) {
            JsonResponses.error(resp, 400, "invalid email");
            return;
        }
        var opt = users.findById(uid);
        if (opt.isEmpty()) {
            JsonResponses.error(resp, 401, "unknown user");
            return;
        }
        var other = users.findByEmail(body.email.strip()).orElse(null);
        if (other != null && !other.getId().equals(uid)) {
            JsonResponses.error(resp, 409, "email already in use");
            return;
        }
        users.updateProfile(uid, body.email.strip(), body.fullName.strip());
        User updated = users.findById(uid).orElseThrow();
        JsonResponses.writeJson(resp, 200, Map.of("ok", true, "user", updated.toPublicView()));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var opt = users.findById(uid);
        if (opt.isEmpty()) {
            JsonResponses.error(resp, 404, "user not found");
            return;
        }
        var user = opt.get();
        if (user.getRole().name().equals("ADMIN") && users.countAdmins() <= 1) {
            JsonResponses.error(resp, 400, "cannot delete the last admin account");
            return;
        }
        users.deleteById(uid);
        var s = req.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        JsonResponses.writeJson(resp, 200, Map.of("deleted", true));
    }

    private static final class UpdateBody {
        String email;
        String fullName;
    }
}
