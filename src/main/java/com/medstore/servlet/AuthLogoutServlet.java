package com.medstore.servlet;

import com.medstore.util.JsonResponses;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "AuthLogoutServlet", urlPatterns = "/api/logout")
public class AuthLogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var s = req.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        JsonResponses.writeJson(resp, 200, Map.of("ok", true));
    }
}
