package com.medstore.dao;

import com.medstore.model.User;
import com.medstore.model.UserRole;
import com.medstore.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UserDAO {

    private static User mapUser(ResultSet rs) throws SQLException {
        String profileImage = null;
        try {
            profileImage = rs.getString("profile_image_path");
        } catch (SQLException ignored) {
            // column missing until migration runs
        }
        return User.hydrate(
                rs.getInt("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                UserRole.valueOf(rs.getString("role")),
                profileImage);
    }

    public int countUsers() {
        try (Connection c = Db.getConnection();
             var st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int insertRegistered(String email, String hash, String fullName, UserRole role) {
        String sql = "INSERT INTO users (email, password_hash, full_name, role) VALUES (?,?,?,?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, hash);
            ps.setString(3, fullName);
            ps.setString(4, role.name());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapUser(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(mapUser(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateProfile(int id, String email, String fullName) {
        String sql = "UPDATE users SET email = ?, full_name = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, fullName);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateByAdmin(int id, String email, String fullName, UserRole role) {
        String sql = "UPDATE users SET email = ?, full_name = ?, role = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, fullName);
            ps.setString(3, role.name());
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateProfileImage(int id, String imagePath) {
        String sql = "UPDATE users SET profile_image_path = ? WHERE id = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, imagePath);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteById(int id) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<User> listAll() {
        try (Connection c = Db.getConnection();
             ResultSet rs = c.createStatement().executeQuery("SELECT * FROM users ORDER BY id")) {
            List<User> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapUser(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int countAdmins() {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
