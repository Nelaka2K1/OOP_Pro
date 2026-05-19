-- Run on existing medstore DB after schema.sql:
-- mysql -u root -p medstore < sql/migration_v2.sql

ALTER TABLE users
    MODIFY role ENUM('CUSTOMER', 'PHARMACIST', 'ADMIN', 'ACCOUNTANT') NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE users
    ADD COLUMN profile_image_path VARCHAR(512) NULL AFTER full_name;

ALTER TABLE medicines
    ADD COLUMN image_path VARCHAR(512) NULL AFTER description;

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
);

CREATE TABLE IF NOT EXISTS prescriptions (
    user_id INT PRIMARY KEY,
    file_path VARCHAR(512) NOT NULL,
    original_name VARCHAR(255),
    content_type VARCHAR(128),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
