package com.medstore.servlet;

import com.medstore.dao.UserDAO;
import com.medstore.model.UserRole;
import com.medstore.servlet.util.Auth;
import com.medstore.service.JdbcPurchaseProcessor;
import com.medstore.service.PurchaseProcessor;
import com.medstore.util.JsonResponses;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "CheckoutServlet", urlPatterns = "/api/checkout")
public class CheckoutServlet extends HttpServlet {

    private final PurchaseProcessor checkout = new JdbcPurchaseProcessor();
    private final UserDAO users = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer uid = Auth.userId(req);
        if (uid == null) {
            JsonResponses.error(resp, 401, "login required");
            return;
        }
        var user = users.findById(uid);
        if (user.isEmpty()) {
            JsonResponses.error(resp, 401, "unknown user");
            return;
        }
        if (user.get().getRole() != UserRole.CUSTOMER) {
            JsonResponses.error(resp, 403, "only customer accounts may purchase");
            return;
        }

        CheckoutBody body = null;
        try {
            body = JsonResponses.gson().fromJson(req.getReader(), CheckoutBody.class);
        } catch (Exception ignored) {
            // optional body
        }
        String paymentMethod = body != null && body.paymentMethod != null ? body.paymentMethod : "Card";
        var u = user.get();

        PurchaseProcessor.CheckoutResult r = checkout.checkout(
                uid, u.getFullName(), u.getEmail(), paymentMethod);
        if (!r.isSuccess()) {
            JsonResponses.error(resp, 400, r.getMessage());
            return;
        }
        JsonResponses.writeJson(resp, 200, Map.of(
                "orderId", r.getOrderId(),
                "receiptId", r.getReceiptId(),
                "total", r.getTotal(),
                "message", r.getMessage()));
    }

    private static final class CheckoutBody {
        String paymentMethod;
        String cardholderName;
    }
}
