package com.medstore.model;

import java.math.BigDecimal;

public final class ReceiptLine {
    private final String medicineName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal lineTotal;

    public ReceiptLine(String medicineName, int quantity, BigDecimal unitPrice) {
        this.medicineName = medicineName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public String getMedicineName() {
        return medicineName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
