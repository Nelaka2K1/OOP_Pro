package com.medstore.servlet;

import com.medstore.dao.UserDAO;
import com.medstore.servlet.util.Auth;
import com.medstore.servlet.util.UserJson;
import com.medstore.util.JsonResponses;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "MeServlet", urlPatterns = "/api/me")
public class MeServlet extends HttpServlet {

    private final UserDAO users = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.writeJson(resp, 200, Map.of("authenticated", false));
            return;
        }
        var opt = users.findById(uid);
        if (opt.isEmpty()) {
            JsonResponses.error(resp, 401, "session invalid");
            return;
        }
        var user = opt.get();
        JsonResponses.writeJson(resp, 200, Map.of(
                "authenticated", true,
                "user", UserJson.toMap(user)));
    }
}
