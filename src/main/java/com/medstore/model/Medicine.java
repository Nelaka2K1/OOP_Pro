package com.medstore.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public final class Medicine {
    private final int id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private int stock;
    private final Integer createdByUserId;
    private final Timestamp createdAt;

    public Medicine(int id, String name, String description, BigDecimal price, int stock,
                    Integer createdByUserId, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public Integer getCreatedByUserId() {
        return createdByUserId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
}
