package com.medstore.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

public final class Receipt {
    private final int id;
    private final int orderId;
    private final int userId;
    private final String receiptNumber;
    private String customerName;
    private String customerEmail;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String notes;
    private final Timestamp issuedAt;
    private List<ReceiptLine> lines;

    public Receipt(int id, int orderId, int userId, String receiptNumber, String customerName,
                   String customerEmail, BigDecimal totalAmount, String paymentMethod, String notes,
                   Timestamp issuedAt) {
        this.id = id;
        this.orderId = orderId;
        this.userId = userId;
        this.receiptNumber = receiptNumber;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.notes = notes;
        this.issuedAt = issuedAt;
    }

    public int getId() {
        return id;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getUserId() {
        return userId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Timestamp getIssuedAt() {
        return issuedAt;
    }

    public List<ReceiptLine> getLines() {
        return lines;
    }

    public void setLines(List<ReceiptLine> lines) {
        this.lines = lines;
    }
}
