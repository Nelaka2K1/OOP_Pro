package com.medstore.servlet;

import com.medstore.dao.MedicineDAO;
import com.medstore.dao.UserDAO;
import com.medstore.model.Medicine;
import com.medstore.servlet.util.Auth;
import com.medstore.util.JsonResponses;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "MedicinesServlet", urlPatterns = "/api/medicines")
public class MedicinesServlet extends HttpServlet {

    private final MedicineDAO medicines = new MedicineDAO();
    private final UserDAO users = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (Auth.userId(req) == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        List<Medicine> rows = medicines.findAllVisible();
        List<Map<String, Object>> json = new ArrayList<>();
        for (Medicine m : rows) {
            json.add(medicineToMap(m));
        }
        JsonResponses.writeJson(resp, 200, Map.of("medicines", json));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var opt = users.findById(uid);
        if (opt.isEmpty()) {
            JsonResponses.error(resp, 401, "unknown user");
            return;
        }
        if (!opt.get().canManageCatalog()) {
            JsonResponses.error(resp, 403, "pharmacist role required to add catalogue items");
            return;
        }
        AddMedicineBody body = JsonResponses.gson().fromJson(req.getReader(), AddMedicineBody.class);
        if (body == null || body.name == null || body.name.isBlank() || body.price == null) {
            JsonResponses.error(resp, 400, "name and price required");
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(body.price);
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "invalid price");
            return;
        }
        int stock = body.stock != null ? body.stock : 0;
        String desc = body.description != null ? body.description : "";
        int id = medicines.insert(body.name.strip(), desc, price, stock, uid);
        JsonResponses.writeJson(resp, 201, Map.of("id", id));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var opt = users.findById(uid);
        if (opt.isEmpty()) {
            JsonResponses.error(resp, 401, "unknown user");
            return;
        }
        if (!opt.get().canManageCatalog()) {
            JsonResponses.error(resp, 403, "pharmacist role required to edit catalogue items");
            return;
        }
        String idStr = req.getParameter("id");
        if (idStr == null || idStr.isBlank()) {
            JsonResponses.error(resp, 400, "id query parameter required");
            return;
        }
        int mid;
        try {
            mid = Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "bad id");
            return;
        }
        if (medicines.findById(mid).isEmpty()) {
            JsonResponses.error(resp, 404, "medicine not found");
            return;
        }
        EditMedicineBody body = JsonResponses.gson().fromJson(req.getReader(), EditMedicineBody.class);
        if (body == null || body.name == null || body.name.isBlank() || body.price == null) {
            JsonResponses.error(resp, 400, "name and price required");
            return;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(body.price);
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "invalid price");
            return;
        }
        int stock = body.stock != null ? body.stock : 0;
        String desc = body.description != null ? body.description : "";
        boolean updated = medicines.update(mid, body.name.strip(), desc, price, stock);
        if (!updated) {
            JsonResponses.error(resp, 409, "update failed");
            return;
        }
        JsonResponses.writeJson(resp, 200, Map.of("updated", mid));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var opt = users.findById(uid);
        if (opt.isEmpty() || !opt.get().canDeleteMedicines()) {
            JsonResponses.error(resp, 403, "pharmacist role required to delete medicines");
            return;
        }
        String idStr = req.getParameter("id");
        if (idStr == null) {
            JsonResponses.error(resp, 400, "id required");
            return;
        }
        int mid;
        try {
            mid = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "bad id");
            return;
        }
        try {
            medicines.deleteById(mid);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException) {
                JsonResponses.error(resp, 409, "medicine tied to archived orders — cannot delete");
                return;
            }
            throw e;
        }
        JsonResponses.writeJson(resp, 200, Map.of("deleted", mid));
    }

    static Map<String, Object> medicineToMap(Medicine m) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", m.getId());
        map.put("name", m.getName());
        map.put("description", m.getDescription());
        map.put("imagePath", m.getImagePath());
        if (m.getImagePath() != null && !m.getImagePath().isBlank()) {
            map.put("imageUrl", UploadServlet.publicUrl(m.getImagePath()));
        }
        map.put("price", m.getPrice());
        map.put("stock", m.getStock());
        map.put("createdByUserId", m.getCreatedByUserId());
        map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toInstant().toString() : null);
        return map;
    }

    private static final class AddMedicineBody {
        String name;
        String description;
        String price;
        Integer stock;
    }

    private static final class EditMedicineBody {
        String name;
        String description;
        String price;
        Integer stock;
    }
}
