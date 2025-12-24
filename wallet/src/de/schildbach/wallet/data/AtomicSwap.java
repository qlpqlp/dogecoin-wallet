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

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity representing an atomic swap transaction
 * 
 * Atomic swaps use Hash Time Lock Contracts (HTLC) to enable
 * trustless cross-chain exchanges (DOGE ↔ BTC, DOGE ↔ LTC)
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
@Entity(
    tableName = "atomic_swaps",
    indices = {
        @Index("status"),
        @Index("fromCurrency"),
        @Index("toCurrency")
    }
)
public class AtomicSwap {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // Swap direction: "DOGE_TO_BTC", "DOGE_TO_LTC", "BTC_TO_DOGE", "LTC_TO_DOGE"
    @NonNull
    public String fromCurrency; // "DOGE", "BTC", or "LTC"
    
    @NonNull
    public String toCurrency; // "DOGE", "BTC", or "LTC"
    
    // Amounts (in smallest unit, e.g., satoshis for BTC, litoshis for LTC, smallest unit for DOGE)
    public long fromAmount;
    public long toAmount;
    
    // HTLC contract details
    @NonNull
    public String secretHash; // SHA256 hash of the secret (32 bytes, hex encoded)
    public String secret; // The secret (revealed when claiming, hex encoded) - null until revealed
    
    // Contract addresses
    public String dogecoinContractAddress; // HTLC contract address on Dogecoin chain
    public String counterpartyContractAddress; // HTLC contract address on counterparty chain (BTC/LTC)
    
    // Counterparty information
    public String counterpartyAddress; // Address of the counterparty (Dogecoin address for DOGE->BTC/LTC swaps)
    public String counterpartyPublicKey; // Public key of counterparty (if available)
    
    // Your receiving address for the other currency
    public String myReceivingAddress; // Your address where you'll receive BTC/LTC (for DOGE->BTC/LTC swaps)
    
    // Status: "PENDING", "CONTRACT_CREATED", "COUNTERPARTY_CONTRACT_CREATED", 
    //         "SECRET_REVEALED", "COMPLETED", "REFUNDED", "FAILED"
    @NonNull
    public String status;
    
    // Timestamps
    public long createdAt; // When swap was initiated
    public long contractCreatedAt; // When our contract was created
    public long counterpartyContractCreatedAt; // When counterparty contract was created
    public long secretRevealedAt; // When secret was revealed
    public long completedAt; // When swap completed
    public long refundedAt; // When refund occurred
    public long expiresAt; // Contract expiration time (block height or timestamp)
    
    // Transaction IDs
    public String dogecoinTxId; // Transaction ID for Dogecoin contract
    public String counterpartyTxId; // Transaction ID for counterparty contract
    public String claimTxId; // Transaction ID when claiming
    public String refundTxId; // Transaction ID when refunding
    
    // Error information
    public String errorMessage; // Error message if swap failed
    
    // Additional metadata
    public String notes; // User notes about the swap
    
    public AtomicSwap() {
        this.status = "PENDING";
        this.createdAt = System.currentTimeMillis();
        // Initialize all timestamp fields to 0 (will be set when used)
        this.contractCreatedAt = 0;
        this.counterpartyContractCreatedAt = 0;
        this.secretRevealedAt = 0;
        this.completedAt = 0;
        this.refundedAt = 0;
        this.expiresAt = 0;
    }
    
    @Ignore
    public AtomicSwap(@NonNull String fromCurrency, @NonNull String toCurrency, 
                     long fromAmount, long toAmount, @NonNull String secretHash) {
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.secretHash = secretHash;
        this.status = "PENDING";
        this.createdAt = System.currentTimeMillis();
        // Initialize all timestamp fields to 0 (will be set when used)
        this.contractCreatedAt = 0;
        this.counterpartyContractCreatedAt = 0;
        this.secretRevealedAt = 0;
        this.completedAt = 0;
        this.refundedAt = 0;
        this.expiresAt = 0;
    }
    
    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    @NonNull
    public String getFromCurrency() { return fromCurrency; }
    public void setFromCurrency(@NonNull String fromCurrency) { this.fromCurrency = fromCurrency; }
    
    @NonNull
    public String getToCurrency() { return toCurrency; }
    public void setToCurrency(@NonNull String toCurrency) { this.toCurrency = toCurrency; }
    
    public long getFromAmount() { return fromAmount; }
    public void setFromAmount(long fromAmount) { this.fromAmount = fromAmount; }
    
    public long getToAmount() { return toAmount; }
    public void setToAmount(long toAmount) { this.toAmount = toAmount; }
    
    @NonNull
    public String getSecretHash() { return secretHash; }
    public void setSecretHash(@NonNull String secretHash) { this.secretHash = secretHash; }
    
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    
    public String getDogecoinContractAddress() { return dogecoinContractAddress; }
    public void setDogecoinContractAddress(String dogecoinContractAddress) { 
        this.dogecoinContractAddress = dogecoinContractAddress; 
    }
    
    public String getCounterpartyContractAddress() { return counterpartyContractAddress; }
    public void setCounterpartyContractAddress(String counterpartyContractAddress) { 
        this.counterpartyContractAddress = counterpartyContractAddress; 
    }
    
    public String getCounterpartyAddress() { return counterpartyAddress; }
    public void setCounterpartyAddress(String counterpartyAddress) { 
        this.counterpartyAddress = counterpartyAddress; 
    }
    
    public String getCounterpartyPublicKey() { return counterpartyPublicKey; }
    public void setCounterpartyPublicKey(String counterpartyPublicKey) { 
        this.counterpartyPublicKey = counterpartyPublicKey; 
    }
    
    public String getMyReceivingAddress() { return myReceivingAddress; }
    public void setMyReceivingAddress(String myReceivingAddress) { 
        this.myReceivingAddress = myReceivingAddress; 
    }
    
    @NonNull
    public String getStatus() { return status; }
    public void setStatus(@NonNull String status) { this.status = status; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getContractCreatedAt() { return contractCreatedAt; }
    public void setContractCreatedAt(long contractCreatedAt) { this.contractCreatedAt = contractCreatedAt; }
    
    public long getCounterpartyContractCreatedAt() { return counterpartyContractCreatedAt; }
    public void setCounterpartyContractCreatedAt(long counterpartyContractCreatedAt) { 
        this.counterpartyContractCreatedAt = counterpartyContractCreatedAt; 
    }
    
    public long getSecretRevealedAt() { return secretRevealedAt; }
    public void setSecretRevealedAt(long secretRevealedAt) { this.secretRevealedAt = secretRevealedAt; }
    
    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    
    public long getRefundedAt() { return refundedAt; }
    public void setRefundedAt(long refundedAt) { this.refundedAt = refundedAt; }
    
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    
    public String getDogecoinTxId() { return dogecoinTxId; }
    public void setDogecoinTxId(String dogecoinTxId) { this.dogecoinTxId = dogecoinTxId; }
    
    public String getCounterpartyTxId() { return counterpartyTxId; }
    public void setCounterpartyTxId(String counterpartyTxId) { this.counterpartyTxId = counterpartyTxId; }
    
    public String getClaimTxId() { return claimTxId; }
    public void setClaimTxId(String claimTxId) { this.claimTxId = claimTxId; }
    
    public String getRefundTxId() { return refundTxId; }
    public void setRefundTxId(String refundTxId) { this.refundTxId = refundTxId; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

