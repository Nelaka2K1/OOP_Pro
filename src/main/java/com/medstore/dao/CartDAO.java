package com.medstore.dao;

import com.medstore.model.CartLine;
import com.medstore.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CartDAO {

    public List<CartLine> listForUser(int userId) {
        String sql = """
                SELECT c.id AS cid, m.id AS mid, m.name, m.price, c.quantity
                FROM cart_items c
                JOIN medicines m ON m.id = c.medicine_id
                WHERE c.user_id = ?
                ORDER BY c.id
                """;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            List<CartLine> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new CartLine(
                        rs.getInt("cid"),
                        rs.getInt("mid"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getInt("quantity")));
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void upsertLine(int userId, int medicineId, int deltaOrSet, boolean replaceQuantity) {
        try (Connection c = Db.getConnection()) {
            if (replaceQuantity) {
                if (deltaOrSet <= 0) {
                    try (PreparedStatement del = c.prepareStatement(
                            "DELETE FROM cart_items WHERE user_id = ? AND medicine_id = ?")) {
                        del.setInt(1, userId);
                        del.setInt(2, medicineId);
                        del.executeUpdate();
                    }
                    return;
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO cart_items (user_id, medicine_id, quantity) VALUES (?,?,?)
                        ON DUPLICATE KEY UPDATE quantity = VALUES(quantity)
                        """)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, medicineId);
                    ps.setInt(3, deltaOrSet);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO cart_items (user_id, medicine_id, quantity) VALUES (?,?,?)
                        ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)
                        """)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, medicineId);
                    ps.setInt(3, deltaOrSet);
                    ps.executeUpdate();
                }
                try (PreparedStatement clean = c.prepareStatement(
                        "DELETE FROM cart_items WHERE user_id = ? AND quantity <= 0")) {
                    clean.setInt(1, userId);
                    clean.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeLine(int userId, int medicineId) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM cart_items WHERE user_id = ? AND medicine_id = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, medicineId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Total items in cart for display badge. */
    public int cartLineCount(int userId) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(quantity),0) FROM cart_items WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
