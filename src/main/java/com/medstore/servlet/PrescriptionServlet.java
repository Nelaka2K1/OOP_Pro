package com.medstore.servlet;

import com.medstore.dao.PrescriptionDAO;
import com.medstore.dao.UserDAO;
import com.medstore.model.UserRole;
import com.medstore.servlet.util.Auth;
import com.medstore.util.JsonResponses;
import com.medstore.util.UploadStorage;
import com.medstore.util.UploadUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "PrescriptionServlet", urlPatterns = "/api/prescription")
@MultipartConfig(maxFileSize = 8_000_000, maxRequestSize = 9_000_000)
public class PrescriptionServlet extends HttpServlet {

    private final PrescriptionDAO prescriptions = new PrescriptionDAO();
    private final UserDAO users = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var user = users.findById(uid).orElse(null);
        if (user == null || user.getRole() != UserRole.CUSTOMER) {
            JsonResponses.error(resp, 403, "patients only");
            return;
        }
        var opt = prescriptions.findByUserId(uid);
        if (opt.isEmpty()) {
            JsonResponses.writeJson(resp, 200, Map.of("prescription", (Object) null));
            return;
        }
        JsonResponses.writeJson(resp, 200, Map.of("prescription", toClientMap(opt.get())));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var user = users.findById(uid).orElse(null);
        if (user == null || user.getRole() != UserRole.CUSTOMER) {
            JsonResponses.error(resp, 403, "patients only");
            return;
        }
        Part filePart = req.getPart("file");
        try {
            Path root = UploadStorage.root(getServletContext());
            Path dir = root.resolve("prescriptions");
            String fileName = UploadUtil.savePrescription(filePart, dir, "rx-user-" + uid);
            String dbPath = "prescriptions/" + fileName;
            String contentType = filePart.getContentType() != null ? filePart.getContentType() : "application/octet-stream";
            String original = filePart.getSubmittedFileName();
            prescriptions.upsert(uid, dbPath, original != null ? original : fileName, contentType);
            var saved = prescriptions.findByUserId(uid).orElseThrow();
            JsonResponses.writeJson(resp, 200, Map.of("ok", true, "prescription", toClientMap(saved)));
        } catch (IOException e) {
            JsonResponses.error(resp, 400, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        prescriptions.deleteByUserId(uid);
        JsonResponses.writeJson(resp, 200, Map.of("deleted", true));
    }

    private Map<String, Object> toClientMap(Map<String, Object> row) {
        Map<String, Object> out = new HashMap<>(row);
        String path = (String) row.get("filePath");
        if (path != null) {
            out.put("fileUrl", UploadServlet.publicUrl(path));
        }
        return out;
    }
}
