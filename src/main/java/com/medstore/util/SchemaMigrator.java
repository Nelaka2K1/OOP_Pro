package com.medstore.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies incremental schema changes so existing databases work without a manual SQL script.
 */
public final class SchemaMigrator {

    private static final Logger LOG = Logger.getLogger(SchemaMigrator.class.getName());

    private SchemaMigrator() {
    }

    public static void migrate() {
        try (Connection c = Db.getConnection()) {
            ensureUsersRoleEnum(c);
            ensureColumn(c, "users", "profile_image_path", "VARCHAR(512) NULL AFTER full_name");
            ensureColumn(c, "medicines", "image_path", "MEDIUMTEXT NULL AFTER description");
            upgradeColumnToMediumText(c, "medicines", "image_path");
            ensureReceiptsTable(c);
            ensurePrescriptionsTable(c);
            backfillMissingReceipts(c);
            LOG.info("Database schema migration check complete");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Schema migration failed — run sql/migration_v2.sql manually", e);
            throw new RuntimeException("Database schema migration failed: " + e.getMessage(), e);
        }
    }

    private static void ensureUsersRoleEnum(Connection c) throws SQLException {
        if (roleEnumIncludesAccountant(c)) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                    ALTER TABLE users
                    MODIFY role ENUM('CUSTOMER', 'PHARMACIST', 'ADMIN', 'ACCOUNTANT')
                    NOT NULL DEFAULT 'CUSTOMER'
                    """);
            LOG.info("Extended users.role enum with ACCOUNTANT");
        }
        if (!roleEnumIncludesAccountant(c)) {
            throw new SQLException("users.role enum still missing ACCOUNTANT after migration");
        }
    }

    private static boolean roleEnumIncludesAccountant(Connection c) throws SQLException {
        String sql = """
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role'
                """;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                return false;
            }
            String type = rs.getString(1);
            return type != null && type.toUpperCase().contains("ACCOUNTANT");
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String definition)
            throws SQLException {
        if (columnExists(c, table, column)) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            LOG.info("Added column " + table + "." + column);
        }
    }

    private static void upgradeColumnToMediumText(Connection c, String table, String column) throws SQLException {
        String sql = """
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return;
            String dataType = rs.getString(1);
            if (!"mediumtext".equalsIgnoreCase(dataType) && !"longtext".equalsIgnoreCase(dataType)) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + column + " MEDIUMTEXT NULL");
                    LOG.info("Upgraded " + table + "." + column + " from " + dataType + " to MEDIUMTEXT");
                }
            }
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private static void ensurePrescriptionsTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS prescriptions (
                        user_id INT PRIMARY KEY,
                        file_path VARCHAR(512) NOT NULL,
                        original_name VARCHAR(255),
                        content_type VARCHAR(128),
                        uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
        }
    }

    /** Create receipts for orders that completed before the receipts feature existed. */
    private static void backfillMissingReceipts(Connection c) throws SQLException {
        String sql = """
                INSERT INTO receipts (order_id, user_id, receipt_number, customer_name, customer_email,
                    total_amount, payment_method, issued_at)
                SELECT o.id, o.user_id,
                    CONCAT('RCP-BF-', o.id),
                    u.full_name, u.email, o.total_amount, 'Card', o.placed_at
                FROM orders o
                JOIN users u ON u.id = o.user_id
                LEFT JOIN receipts r ON r.order_id = o.id
                WHERE r.id IS NULL
                """;
        try (Statement st = c.createStatement()) {
            int n = st.executeUpdate(sql);
            if (n > 0) {
                LOG.info("Backfilled " + n + " missing receipt(s) from existing orders");
            }
        }
    }

    private static void ensureReceiptsTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS receipts (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        order_id INT NOT NULL UNIQUE,
                        user_id INT NOT NULL,
                        receipt_number VARCHAR(40) NOT NULL UNIQUE,
                        customer_name VARCHAR(255) NOT NULL,
                        customer_email VARCHAR(255) NOT NULL,
                        total_amount DECIMAL(12, 2) NOT NULL,
                        payment_method VARCHAR(64) NOT NULL DEFAULT 'Card',
                        notes TEXT,
                        issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
        }
    }
}
