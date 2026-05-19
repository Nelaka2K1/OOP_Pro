package com.medstore.dao;

import com.medstore.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class PrescriptionDAO {

    public void upsert(int userId, String filePath, String originalName, String contentType) {
        String sql = """
                INSERT INTO prescriptions (user_id, file_path, original_name, content_type)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE
                file_path = VALUES(file_path),
                original_name = VALUES(original_name),
                content_type = VALUES(content_type),
                uploaded_at = CURRENT_TIMESTAMP
                """;
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, filePath);
            ps.setString(3, originalName);
            ps.setString(4, contentType);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<Map<String, Object>> findByUserId(int userId) {
        String sql = "SELECT file_path, original_name, content_type, uploaded_at FROM prescriptions WHERE user_id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("filePath", rs.getString("file_path"));
                map.put("originalName", rs.getString("original_name"));
                map.put("contentType", rs.getString("content_type"));
                Timestamp ts = rs.getTimestamp("uploaded_at");
                map.put("uploadedAt", ts != null ? ts.toInstant().toString() : null);
                return Optional.of(map);
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteByUserId(int userId) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM prescriptions WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
