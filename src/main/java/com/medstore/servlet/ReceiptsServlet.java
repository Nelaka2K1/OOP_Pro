package com.medstore.servlet;

import com.medstore.dao.ReceiptDAO;
import com.medstore.dao.UserDAO;
import com.medstore.model.Receipt;
import com.medstore.model.ReceiptLine;
import com.medstore.model.UserRole;
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

@WebServlet(name = "ReceiptsServlet", urlPatterns = "/api/receipts")
public class ReceiptsServlet extends HttpServlet {

    private final ReceiptDAO receipts = new ReceiptDAO();
    private final UserDAO users = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

        String idStr = req.getParameter("id");
        if (idStr != null && !idStr.isBlank()) {
            int rid;
            try {
                rid = Integer.parseInt(idStr.trim());
            } catch (NumberFormatException e) {
                JsonResponses.error(resp, 400, "bad id");
                return;
            }
            var opt = receipts.findById(rid);
            if (opt.isEmpty()) {
                JsonResponses.error(resp, 404, "receipt not found");
                return;
            }
            Receipt r = opt.get();
            if (!canView(user.getRole(), uid, r)) {
                JsonResponses.error(resp, 403, "forbidden");
                return;
            }
            JsonResponses.writeJson(resp, 200, Map.of("receipt", receiptToMap(r, true)));
            return;
        }

        List<Receipt> list;
        UserRole role = user.getRole();
        if (role == UserRole.ACCOUNTANT || user.canManageReceipts()) {
            try {
                list = receipts.findAll();
            } catch (RuntimeException e) {
                getServletContext().log("Receipt list failed", e);
                JsonResponses.error(resp, 503,
                        "Receipts table unavailable — restart the server to run database migration");
                return;
            }
        } else if (role == UserRole.CUSTOMER) {
            list = receipts.findForUser(uid);
        } else {
            JsonResponses.error(resp, 403, "forbidden");
            return;
        }
        List<Map<String, Object>> json = new ArrayList<>();
        for (Receipt r : list) {
            json.add(receiptToMap(r, false));
        }
        JsonResponses.writeJson(resp, 200, Map.of("receipts", json));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var userOpt = users.findById(uid);
        if (userOpt.isEmpty() || !userOpt.get().canManageReceipts()) {
            JsonResponses.error(resp, 403, "accountant role required");
            return;
        }
        String idStr = req.getParameter("id");
        if (idStr == null) {
            JsonResponses.error(resp, 400, "id required");
            return;
        }
        int rid = Integer.parseInt(idStr.trim());
        if (receipts.findById(rid).isEmpty()) {
            JsonResponses.error(resp, 404, "receipt not found");
            return;
        }
        UpdateBody body = JsonResponses.gson().fromJson(req.getReader(), UpdateBody.class);
        if (body == null || body.customerName == null || body.customerEmail == null || body.totalAmount == null) {
            JsonResponses.error(resp, 400, "customerName, customerEmail, totalAmount required");
            return;
        }
        BigDecimal total;
        try {
            total = new BigDecimal(body.totalAmount);
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "invalid total");
            return;
        }
        String method = body.paymentMethod != null ? body.paymentMethod : "Card";
        receipts.update(rid, body.customerName.strip(), body.customerEmail.strip(), total, method,
                body.notes != null ? body.notes : "");
        var updated = receipts.findById(rid).orElseThrow();
        JsonResponses.writeJson(resp, 200, Map.of("receipt", receiptToMap(updated, true)));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var userOpt = users.findById(uid);
        if (userOpt.isEmpty() || !userOpt.get().canManageReceipts()) {
            JsonResponses.error(resp, 403, "accountant role required");
            return;
        }
        String idStr = req.getParameter("id");
        if (idStr == null) {
            JsonResponses.error(resp, 400, "id required");
            return;
        }
        int rid = Integer.parseInt(idStr.trim());
        if (receipts.findById(rid).isEmpty()) {
            JsonResponses.error(resp, 404, "receipt not found");
            return;
        }
        receipts.deleteById(rid);
        JsonResponses.writeJson(resp, 200, Map.of("deleted", rid));
    }

    private static boolean canView(UserRole role, int uid, Receipt r) {
        if (role == UserRole.ACCOUNTANT) {
            return true;
        }
        return role == UserRole.CUSTOMER && r.getUserId() == uid;
    }

    static Map<String, Object> receiptToMap(Receipt r, boolean includeLines) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", r.getId());
        map.put("orderId", r.getOrderId());
        map.put("userId", r.getUserId());
        map.put("receiptNumber", r.getReceiptNumber());
        map.put("customerName", r.getCustomerName());
        map.put("customerEmail", r.getCustomerEmail());
        map.put("totalAmount", r.getTotalAmount());
        map.put("paymentMethod", r.getPaymentMethod());
        map.put("notes", r.getNotes());
        map.put("issuedAt", r.getIssuedAt() != null ? r.getIssuedAt().toInstant().toString() : null);
        if (includeLines && r.getLines() != null) {
            List<Map<String, Object>> lines = new ArrayList<>();
            for (ReceiptLine line : r.getLines()) {
                Map<String, Object> lm = new HashMap<>();
                lm.put("medicineName", line.getMedicineName());
                lm.put("quantity", line.getQuantity());
                lm.put("unitPrice", line.getUnitPrice());
                lm.put("lineTotal", line.getLineTotal());
                lines.add(lm);
            }
            map.put("lines", lines);
        }
        return map;
    }

    private static final class UpdateBody {
        String customerName;
        String customerEmail;
        String totalAmount;
        String paymentMethod;
        String notes;
    }
}
