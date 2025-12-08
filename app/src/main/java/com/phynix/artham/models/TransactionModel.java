package com.phynix.artham.models;

import java.io.Serializable;

public class TransactionModel implements Serializable {
    private String transactionId;
    private String transactionCategory;
    private String partyName;
    private double amount;
    private String type; // "IN" or "OUT"
    private String paymentMode; // e.g., "Cash", "Online", "Card"
    private String remark;
    private long timestamp;

    // [NEW FIELDS for Details Dialog]
    private String tags;
    private String location;
    private String attachmentUri;

    public TransactionModel() {
        // Default constructor required for Firebase
    }

    // --- Getters and Setters ---

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getTransactionCategory() { return transactionCategory; }
    public void setTransactionCategory(String transactionCategory) { this.transactionCategory = transactionCategory; }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    // [NEW GETTERS/SETTERS]
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getAttachmentUri() { return attachmentUri; }
    public void setAttachmentUri(String attachmentUri) { this.attachmentUri = attachmentUri; }
}