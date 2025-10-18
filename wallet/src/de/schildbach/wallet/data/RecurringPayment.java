package de.schildbach.wallet.data;

import java.util.Date;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */

public class RecurringPayment {
    private long id;
    private String destinationAddress;
    private String reference;
    private double amount;
    private String sendingAddressLabel;
    private String sendingAddress;
    private Date nextPaymentDate;
    private boolean recurringMonthly;
    private boolean enabled;
    private Date createdAt;
    private Date lastPaymentDate;
    
    public RecurringPayment() {
        this.createdAt = new Date();
        this.enabled = true;
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public String getDestinationAddress() {
        return destinationAddress;
    }
    
    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }
    
    public String getReference() {
        return reference;
    }
    
    public void setReference(String reference) {
        this.reference = reference;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public String getSendingAddressLabel() {
        return sendingAddressLabel;
    }
    
    public void setSendingAddressLabel(String sendingAddressLabel) {
        this.sendingAddressLabel = sendingAddressLabel;
    }
    
    public String getSendingAddress() {
        return sendingAddress;
    }
    
    public void setSendingAddress(String sendingAddress) {
        this.sendingAddress = sendingAddress;
    }
    
    public Date getNextPaymentDate() {
        return nextPaymentDate;
    }
    
    public void setNextPaymentDate(Date nextPaymentDate) {
        this.nextPaymentDate = nextPaymentDate;
    }
    
    public boolean isRecurringMonthly() {
        return recurringMonthly;
    }
    
    public void setRecurringMonthly(boolean recurringMonthly) {
        this.recurringMonthly = recurringMonthly;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Date getLastPaymentDate() {
        return lastPaymentDate;
    }
    
    public void setLastPaymentDate(Date lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }
    
    @Override
    public String toString() {
        return "RecurringPayment{" +
                "id=" + id +
                ", destinationAddress='" + destinationAddress + '\'' +
                ", amount=" + amount +
                ", sendingAddressLabel='" + sendingAddressLabel + '\'' +
                ", nextPaymentDate=" + nextPaymentDate +
                ", recurringMonthly=" + recurringMonthly +
                ", enabled=" + enabled +
                '}';
    }
}
