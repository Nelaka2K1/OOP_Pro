package com.medstore.dao;

import com.medstore.model.Medicine;
import com.medstore.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MedicineDAO {

    private static Medicine mapRow(ResultSet rs) throws SQLException {
        String imagePath = null;
        try {
            imagePath = rs.getString("image_path");
        } catch (SQLException ignored) {
            // column missing until migration runs
        }
        return new Medicine(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("description"),
                imagePath,
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getObject("created_by_user_id") != null ? rs.getInt("created_by_user_id") : null,
                rs.getTimestamp("created_at"));
    }

    public List<Medicine> findAllVisible() {
        String sql = "SELECT * FROM medicines ORDER BY id DESC";
        try (Connection c = Db.getConnection();
             ResultSet rs = c.createStatement().executeQuery(sql)) {
            List<Medicine> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapRow(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int insert(String name, String description, java.math.BigDecimal price, int stock, int pharmacistId) {
        String sql = """
                INSERT INTO medicines (name, description, price, stock, created_by_user_id)
                VALUES (?,?,?,?,?)
                """;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setBigDecimal(3, price);
            ps.setInt(4, stock);
            ps.setInt(5, pharmacistId);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean update(int id, String name, String description, java.math.BigDecimal price, int stock) {
        String sql = "UPDATE medicines SET name = ?, description = ?, price = ?, stock = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setBigDecimal(3, price);
            ps.setInt(4, stock);
            ps.setInt(5, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateImagePath(int id, String imagePath) {
        String sql = "UPDATE medicines SET image_path = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, imagePath);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteById(int medicineId) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM medicines WHERE id = ?")) {
            ps.setInt(1, medicineId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Medicine> findById(int id) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM medicines WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
