package com.medstore.model;

import java.math.BigDecimal;

public final class CartLine {
    private final int cartItemId;
    private final int medicineId;
    private final String medicineName;
    private final BigDecimal unitPrice;
    private int quantity;

    public CartLine(int cartItemId, int medicineId, String medicineName, BigDecimal unitPrice, int quantity) {
        this.cartItemId = cartItemId;
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public int getCartItemId() {
        return cartItemId;
    }

    public int getMedicineId() {
        return medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
