package com.medstore.service;

import com.medstore.dao.ReceiptDAO;
import com.medstore.model.CartLine;
import com.medstore.util.Db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class JdbcPurchaseProcessor implements PurchaseProcessor {

    private final ReceiptDAO receipts = new ReceiptDAO();

    @Override
    public CheckoutResult checkout(int customerUserId, String customerName, String customerEmail, String paymentMethod) {
        try (Connection conn = Db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<CartLine> lines = loadCart(conn, customerUserId);
                if (lines.isEmpty()) {
                    conn.rollback();
                    return new CheckoutResult(false, "Cart is empty", null, null, null);
                }

                BigDecimal total = BigDecimal.ZERO;
                for (CartLine line : lines) {
                    int avail = lockedStock(conn, line.getMedicineId());
                    if (avail < line.getQuantity()) {
                        conn.rollback();
                        return new CheckoutResult(false,
                                "Insufficient stock for: " + line.getMedicineName(), null, null, null);
                    }
                    total = total.add(line.lineTotal());
                }

                int orderId = insertOrder(conn, customerUserId, total);
                for (CartLine line : lines) {
                    insertOrderLine(conn, orderId, line);
                    decrementStock(conn, line.getMedicineId(), line.getQuantity());
                }
                clearCart(conn, customerUserId);

                String method = paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod.strip() : "Card";
                int receiptId = receipts.insert(conn, orderId, customerUserId,
                        customerName, customerEmail, total, method);

                conn.commit();
                return new CheckoutResult(true, "Payment successful", orderId, receiptId, total);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<CartLine> loadCart(Connection conn, int userId) throws SQLException {
        var cart = new java.util.ArrayList<CartLine>();
        String sql = """
                SELECT c.id AS cid, m.id AS mid, m.name, m.price, c.quantity
                FROM cart_items c
                JOIN medicines m ON m.id = c.medicine_id
                WHERE c.user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                cart.add(new CartLine(
                        rs.getInt("cid"),
                        rs.getInt("mid"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getInt("quantity")));
            }
        }
        return cart;
    }

    private static int lockedStock(Connection conn, int medicineId) throws SQLException {
        String sql = "SELECT stock FROM medicines WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new SQLException("medicine missing: " + medicineId);
            }
            return rs.getInt("stock");
        }
    }

    private static int insertOrder(Connection conn, int userId, BigDecimal total) throws SQLException {
        String sql = "INSERT INTO orders (user_id, total_amount) VALUES (?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setBigDecimal(2, total);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getInt(1);
        }
    }

    private static void insertOrderLine(Connection conn, int orderId, CartLine line) throws SQLException {
        String sql = "INSERT INTO order_lines (order_id, medicine_id, quantity, unit_price) VALUES (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, line.getMedicineId());
            ps.setInt(3, line.getQuantity());
            ps.setBigDecimal(4, line.getUnitPrice());
            ps.executeUpdate();
        }
    }

    private static void decrementStock(Connection conn, int medicineId, int qty) throws SQLException {
        String sql = "UPDATE medicines SET stock = stock - ? WHERE id = ? AND stock >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, medicineId);
            ps.setInt(3, qty);
            int n = ps.executeUpdate();
            if (n != 1) {
                throw new SQLException("stock update conflict");
            }
        }
    }

    private static void clearCart(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM cart_items WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }
}
