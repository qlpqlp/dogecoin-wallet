/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import java.util.Date;

/**
 * Represents a Dogecoin check (timelock transaction)
 */
@Entity(tableName = "checks")
@TypeConverters({DateConverter.class})
public class Check {
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String payTo;
    private Date date; // Date when transaction can be spent
    private Date expirationDate; // Date when check expires (if not swept, funds are returned)
    private long amount; // Amount in smallest units
    private String memo;
    private String signature; // Owner's name or "Anonymous"
    private String address; // Derived address for the check
    private String derivedKey; // Private key for the check (WIF format - single address, single key)
    private String transactionHash; // Transaction hash if sent
    private Date createdAt; // When the check was created
    private boolean isSpent; // Whether the check has been spent
    private String status; // Status: "active", "canceled", "spent"
    
    public Check() {
        this.createdAt = new Date();
        this.isSpent = false;
        this.status = "active";
    }
    
    @androidx.room.Ignore
    public Check(String payTo, Date date, Date expirationDate, long amount, String memo, String signature, 
                 String address, String derivedKey) {
        this();
        this.payTo = payTo;
        this.date = date;
        this.expirationDate = expirationDate;
        this.amount = amount;
        this.memo = memo;
        this.signature = signature;
        this.address = address;
        this.derivedKey = derivedKey;
    }
    
    // Getters and setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public String getPayTo() {
        return payTo;
    }
    
    public void setPayTo(String payTo) {
        this.payTo = payTo;
    }
    
    public Date getDate() {
        return date;
    }
    
    public void setDate(Date date) {
        this.date = date;
    }
    
    public long getAmount() {
        return amount;
    }
    
    public void setAmount(long amount) {
        this.amount = amount;
    }
    
    public String getMemo() {
        return memo;
    }
    
    public void setMemo(String memo) {
        this.memo = memo;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(String signature) {
        this.signature = signature;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getDerivedKey() {
        return derivedKey;
    }
    
    public void setDerivedKey(String derivedKey) {
        this.derivedKey = derivedKey;
    }
    
    public String getTransactionHash() {
        return transactionHash;
    }
    
    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public boolean isSpent() {
        return isSpent;
    }
    
    public void setSpent(boolean spent) {
        isSpent = spent;
    }
    
    public Date getExpirationDate() {
        return expirationDate;
    }
    
    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }
    
    public String getStatus() {
        return status != null ? status : "active";
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}

