package com.medstore.servlet;

import com.medstore.dao.MedicineDAO;
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
import java.util.Map;

@WebServlet(name = "UploadServlet", urlPatterns = "/api/upload")
@MultipartConfig(fileSizeThreshold = 0, maxFileSize = 8_000_000, maxRequestSize = 9_000_000)
public class UploadServlet extends HttpServlet {

    private final MedicineDAO medicines = new MedicineDAO();
    private final UserDAO users = new UserDAO();

    /** URL path the browser uses (under the app context). */
    public static String publicUrl(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            return null;
        }
        return "api/media/" + dbPath.replace('\\', '/');
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var userOpt = users.findById(uid);
        if (userOpt.isEmpty()) {
            JsonResponses.error(resp, 401, "unknown user");
            return;
        }
        var user = userOpt.get();

        String type = req.getParameter("type");
        if (type == null || type.isBlank()) {
            JsonResponses.error(resp, 400, "type required (medicine or profile)");
            return;
        }

        Part filePart;
        try {
            filePart = req.getPart("file");
        } catch (ServletException e) {
            JsonResponses.error(resp, 400, "multipart upload required — send field name 'file'");
            return;
        }

        Path uploadsRoot = UploadStorage.root(getServletContext());

        try {
            if ("profile".equalsIgnoreCase(type)) {
                if (user.getRole() != UserRole.CUSTOMER) {
                    JsonResponses.error(resp, 403, "only patients may upload profile photos");
                    return;
                }
                Path dir = uploadsRoot.resolve("profiles");
                String fileName = UploadUtil.saveImage(filePart, dir, "user-" + uid);
                String dbPath = "profiles/" + fileName;
                users.updateProfileImage(uid, dbPath);
                user.setProfileImagePath(dbPath);
                JsonResponses.writeJson(resp, 200, Map.of(
                        "ok", true,
                        "imagePath", dbPath,
                        "imageUrl", publicUrl(dbPath),
                        "user", com.medstore.servlet.util.UserJson.toMap(user)));
                return;
            }

            if ("medicine".equalsIgnoreCase(type)) {
                if (!user.canManageCatalog()) {
                    JsonResponses.error(resp, 403, "pharmacist role required");
                    return;
                }
                String midStr = req.getParameter("medicineId");
                if (midStr == null || midStr.isBlank()) {
                    JsonResponses.error(resp, 400, "medicineId required");
                    return;
                }
                int mid = Integer.parseInt(midStr.trim());
                if (medicines.findById(mid).isEmpty()) {
                    JsonResponses.error(resp, 404, "medicine not found");
                    return;
                }
                Path dir = uploadsRoot.resolve("medicines");
                String fileName = UploadUtil.saveImage(filePart, dir, "med-" + mid);
                String dbPath = "medicines/" + fileName;
                medicines.updateImagePath(mid, dbPath);
                JsonResponses.writeJson(resp, 200, Map.of(
                        "ok", true,
                        "imagePath", dbPath,
                        "imageUrl", publicUrl(dbPath),
                        "medicineId", mid));
                return;
            }

            JsonResponses.error(resp, 400, "unknown type");
        } catch (IOException e) {
            JsonResponses.error(resp, 400, e.getMessage());
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "bad medicineId");
        } catch (RuntimeException e) {
            getServletContext().log("Upload failed", e);
            String msg = e.getCause() instanceof java.sql.SQLException
                    ? "database error — restart server to apply schema updates"
                    : e.getMessage();
            JsonResponses.error(resp, 500, msg != null ? msg : "upload failed");
        }
    }
}
