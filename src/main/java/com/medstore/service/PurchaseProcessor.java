package com.medstore.service;

import java.math.BigDecimal;

/**
 * Polymorphism: order placement can swap implementations (e.g. test double) while keeping callers stable.
 */
public interface PurchaseProcessor {
    CheckoutResult checkout(int customerUserId, String customerName, String customerEmail, String paymentMethod);

    final class CheckoutResult {
        private final boolean success;
        private final String message;
        private final Integer orderId;
        private final Integer receiptId;
        private final BigDecimal total;

        public CheckoutResult(boolean success, String message, Integer orderId, Integer receiptId, BigDecimal total) {
            this.success = success;
            this.message = message;
            this.orderId = orderId;
            this.receiptId = receiptId;
            this.total = total;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Integer getOrderId() {
            return orderId;
        }

        public Integer getReceiptId() {
            return receiptId;
        }

        public BigDecimal getTotal() {
            return total;
        }
    }
}
