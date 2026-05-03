package com.medstore.servlet;

import com.medstore.dao.UserDAO;
import com.medstore.servlet.util.Auth;
import com.medstore.util.JsonResponses;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet(name = "AdminUsersServlet", urlPatterns = "/api/admin/users")
public class AdminUsersServlet extends HttpServlet {

    private final UserDAO users = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var me = users.findById(uid);
        if (me.isEmpty() || !me.get().canDeleteUsers()) {
            JsonResponses.error(resp, 403, "admin role required");
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var u : users.listAll()) {
            var v = u.toPublicView();
            out.add(Map.of(
                    "id", v.getId(),
                    "email", v.getEmail(),
                    "fullName", v.getFullName(),
                    "role", v.getRole()));
        }
        JsonResponses.writeJson(resp, 200, Map.of("users", out));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer callerId = Auth.userId(req);
        if (callerId == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var me = users.findById(callerId);
        if (me.isEmpty() || !me.get().canDeleteUsers()) {
            JsonResponses.error(resp, 403, "admin role required");
            return;
        }
        String idStr = req.getParameter("id");
        if (idStr == null) {
            JsonResponses.error(resp, 400, "id required");
            return;
        }
        int victimId;
        try {
            victimId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "bad id");
            return;
        }
        var victim = users.findById(victimId);
        if (victim.isEmpty()) {
            JsonResponses.error(resp, 404, "user not found");
            return;
        }
        if (victim.get().getRole().name().equals("ADMIN") && users.countAdmins() <= 1) {
            JsonResponses.error(resp, 400, "cannot delete the last admin");
            return;
        }
        if (victimId == callerId) {
            JsonResponses.error(resp, 400, "use account settings to delete your own login");
            return;
        }
        users.deleteById(victimId);
        JsonResponses.writeJson(resp, 200, Map.of("deletedUserId", victimId));
    }
}
