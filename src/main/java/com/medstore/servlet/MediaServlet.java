package com.medstore.servlet;

import com.medstore.util.UploadStorage;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves uploaded files at /api/media/medicines/..., /api/media/profiles/..., etc. */
@WebServlet(name = "MediaServlet", urlPatterns = "/api/media/*")
public class MediaServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String relative = pathInfo.substring(1).replace('\\', '/');
        try {
            Path file = UploadStorage.resolve(getServletContext(), relative);
            if (!Files.isRegularFile(file)) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String mime = Files.probeContentType(file);
            if (mime == null) {
                mime = guessMime(relative);
            }
            resp.setContentType(mime);
            resp.setHeader("Cache-Control", "private, max-age=3600");
            resp.setContentLengthLong(Files.size(file));
            Files.copy(file, resp.getOutputStream());
        } catch (IOException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private static String guessMime(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "image/jpeg";
    }
}
