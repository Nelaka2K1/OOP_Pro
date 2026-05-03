package com.medstore.servlet;

import com.medstore.dao.CartDAO;
import com.medstore.dao.UserDAO;
import com.medstore.model.CartLine;
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

@WebServlet(name = "CartServlet", urlPatterns = "/api/cart")
public class CartServlet extends HttpServlet {

    private final CartDAO cart = new CartDAO();
    private final UserDAO users = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = customerOr403(req, resp);
        if (uid == null) {
            return;
        }
        List<CartLine> lines = cart.listForUser(uid);
        List<Map<String, Object>> js = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (CartLine l : lines) {
            grand = grand.add(l.lineTotal());
            Map<String, Object> row = new HashMap<>();
            row.put("cartItemId", l.getCartItemId());
            row.put("medicineId", l.getMedicineId());
            row.put("name", l.getMedicineName());
            row.put("unitPrice", l.getUnitPrice());
            row.put("quantity", l.getQuantity());
            row.put("lineTotal", l.lineTotal());
            js.add(row);
        }
        JsonResponses.writeJson(resp, 200, Map.of("lines", js, "total", grand));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = customerOr403(req, resp);
        if (uid == null) {
            return;
        }
        LineBody body = JsonResponses.gson().fromJson(req.getReader(), LineBody.class);
        if (body == null || body.medicineId == null || body.quantity == null) {
            JsonResponses.error(resp, 400, "medicineId and quantity required");
            return;
        }
        if (body.quantity <= 0) {
            JsonResponses.error(resp, 400, "quantity must be positive");
            return;
        }
        cart.upsertLine(uid, body.medicineId, body.quantity, false);
        JsonResponses.writeJson(resp, 200, Map.of("ok", true));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = customerOr403(req, resp);
        if (uid == null) {
            return;
        }
        LineBody body = JsonResponses.gson().fromJson(req.getReader(), LineBody.class);
        if (body == null || body.medicineId == null || body.quantity == null) {
            JsonResponses.error(resp, 400, "medicineId and quantity required");
            return;
        }
        cart.upsertLine(uid, body.medicineId, body.quantity, true);
        JsonResponses.writeJson(resp, 200, Map.of("ok", true));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = customerOr403(req, resp);
        if (uid == null) {
            return;
        }
        String mid = req.getParameter("medicineId");
        if (mid == null) {
            JsonResponses.error(resp, 400, "medicineId required");
            return;
        }
        try {
            cart.removeLine(uid, Integer.parseInt(mid));
        } catch (NumberFormatException e) {
            JsonResponses.error(resp, 400, "bad medicineId");
            return;
        }
        JsonResponses.writeJson(resp, 200, Map.of("ok", true));
    }

    private Integer customerOr403(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return null;
        }
        var user = users.findById(uid);
        if (user.isEmpty()) {
            JsonResponses.error(resp, 401, "unknown user");
            return null;
        }
        if (user.get().getRole() != UserRole.CUSTOMER) {
            JsonResponses.error(resp, 403, "only patient accounts may use the shopping cart");
            return null;
        }
        return uid;
    }

    private static final class LineBody {
        Integer medicineId;
        Integer quantity;
    }
}
