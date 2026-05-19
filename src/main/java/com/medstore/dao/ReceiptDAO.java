package com.medstore.dao;

import com.medstore.model.Receipt;
import com.medstore.model.ReceiptLine;
import com.medstore.util.Db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReceiptDAO {

    private static Receipt mapReceipt(ResultSet rs) throws SQLException {
        return new Receipt(
                rs.getInt("id"),
                rs.getInt("order_id"),
                rs.getInt("user_id"),
                rs.getString("receipt_number"),
                rs.getString("customer_name"),
                rs.getString("customer_email"),
                rs.getBigDecimal("total_amount"),
                rs.getString("payment_method"),
                rs.getString("notes"),
                rs.getTimestamp("issued_at"));
    }

    public int insert(Connection conn, int orderId, int userId, String customerName, String customerEmail,
                      BigDecimal total, String paymentMethod) throws SQLException {
        String receiptNumber = nextReceiptNumber(conn);
        String sql = """
                INSERT INTO receipts (order_id, user_id, receipt_number, customer_name, customer_email,
                total_amount, payment_method)
                VALUES (?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            ps.setString(3, receiptNumber);
            ps.setString(4, customerName);
            ps.setString(5, customerEmail);
            ps.setBigDecimal(6, total);
            ps.setString(7, paymentMethod != null ? paymentMethod : "Card");
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getInt(1);
        }
    }

    private static String nextReceiptNumber(Connection conn) throws SQLException {
        String prefix = "RCP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        String sql = "SELECT COUNT(*) FROM receipts WHERE receipt_number LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            rs.next();
            int seq = rs.getInt(1) + 1;
            return prefix + String.format("%05d", seq);
        }
    }

    public List<Receipt> findForUser(int userId) {
        return listWhere("WHERE r.user_id = ? ORDER BY r.issued_at DESC", userId);
    }

    public List<Receipt> findAll() {
        return listWhere("ORDER BY r.issued_at DESC", null);
    }

    private List<Receipt> listWhere(String clause, Integer userId) {
        String sql = "SELECT r.* FROM receipts r " + clause;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            }
            ResultSet rs = ps.executeQuery();
            List<Receipt> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapReceipt(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Receipt> findById(int id) {
        String sql = "SELECT * FROM receipts WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Receipt r = mapReceipt(rs);
                r.setLines(loadLines(c, r.getOrderId()));
                return Optional.of(r);
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Receipt> findByOrderId(int orderId) {
        String sql = "SELECT * FROM receipts WHERE order_id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Receipt r = mapReceipt(rs);
                r.setLines(loadLines(c, orderId));
                return Optional.of(r);
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<ReceiptLine> loadLines(Connection c, int orderId) throws SQLException {
        String sql = """
                SELECT m.name, ol.quantity, ol.unit_price
                FROM order_lines ol
                JOIN medicines m ON m.id = ol.medicine_id
                WHERE ol.order_id = ?
                """;
        List<ReceiptLine> lines = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lines.add(new ReceiptLine(
                        rs.getString("name"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price")));
            }
        }
        return lines;
    }

    public boolean update(int id, String customerName, String customerEmail, BigDecimal totalAmount,
                          String paymentMethod, String notes) {
        String sql = """
                UPDATE receipts SET customer_name = ?, customer_email = ?, total_amount = ?,
                payment_method = ?, notes = ? WHERE id = ?
                """;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customerName);
            ps.setString(2, customerEmail);
            ps.setBigDecimal(3, totalAmount);
            ps.setString(4, paymentMethod);
            ps.setString(5, notes);
            ps.setInt(6, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteById(int id) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM receipts WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
