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

package de.schildbach.wallet.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Service to handle retrying pending transactions via RadioDoge when conditions are met
 */
public class PendingTransactionRetryService {
    private static final Logger log = LoggerFactory.getLogger(PendingTransactionRetryService.class);
    
    private final Context context;
    private final Handler mainHandler;
    
    public PendingTransactionRetryService(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Check for pending transactions and attempt to broadcast them via RadioDoge
     */
    public void retryPendingTransactions() {
        // Run the retry logic in a background thread to avoid blocking UI
        new Thread(() -> {
            try {
                WalletApplication app = (WalletApplication) context.getApplicationContext();
                Configuration config = app.getConfiguration();
                
                // Check if RadioDoge is enabled
                if (!config.getRadioDogeEnabled()) {
                    log.info("RadioDoge is disabled, skipping pending transaction retry");
                    return;
                }
                
                // Check if we should use RadioDoge (no internet, connected to RadioDoge)
                if (RadioDogeHelper.hasInternetConnectivity(context)) {
                    log.info("Internet available, skipping RadioDoge pending transaction retry");
                    return;
                }
                
                if (!RadioDogeHelper.isConnectedToRadioDoge(context)) {
                    log.info("Not connected to RadioDoge WiFi, skipping pending transaction retry");
                    return;
                }
                
                // Get pending transactions from wallet
                Wallet wallet = app.getWallet();
                if (wallet == null) {
                    log.warn("Wallet not available for pending transaction retry");
                    return;
                }
                
                Collection<Transaction> pendingTransactions = wallet.getPendingTransactions();
                if (pendingTransactions.isEmpty()) {
                    log.info("No pending transactions to retry");
                    return;
                }
                
                log.info("Found {} pending transactions, attempting RadioDoge broadcast", pendingTransactions.size());
                
                // Retry each pending transaction
                for (Transaction transaction : pendingTransactions) {
                    retryTransactionViaRadioDoge(transaction);
                }
                
            } catch (Exception e) {
                log.warn("Error retrying pending transactions: {}", e.getMessage());
            }
        }).start();
    }
    
    /**
     * Retry a specific transaction via RadioDoge
     */
    private void retryTransactionViaRadioDoge(final Transaction transaction) {
        log.info("Retrying pending transaction {} via RadioDoge", transaction.getTxId());
        
        RadioDogeHelper.broadcastTransactionViaRadioDoge(transaction, new RadioDogeHelper.RadioDogeCallback() {
            @Override
            public void onSuccess() {
                log.info("RadioDoge broadcast successful for pending transaction: {}", transaction.getTxId());
            }
            
            @Override
            public void onFailure(String error) {
                log.warn("RadioDoge broadcast failed for pending transaction {}: {}", transaction.getTxId(), error);
            }
        });
    }
    
    /**
     * Check if there are any pending transactions that could be retried
     */
    public boolean hasPendingTransactions() {
        try {
            WalletApplication app = (WalletApplication) context.getApplicationContext();
            Wallet wallet = app.getWallet();
            if (wallet == null) {
                return false;
            }
            
            return !wallet.getPendingTransactions().isEmpty();
        } catch (Exception e) {
            log.warn("Error checking for pending transactions: {}", e.getMessage());
            return false;
        }
    }
}
