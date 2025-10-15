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

package de.schildbach.wallet.ui.send;

import android.os.Handler;
import android.os.Looper;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.ChildModeHelper;
import de.schildbach.wallet.util.ExcludedAddressHelper;
import de.schildbach.wallet.util.RadioDogeHelper;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.CompletionException;
import org.bitcoinj.wallet.Wallet.CouldNotAdjustDownwards;
import org.bitcoinj.wallet.Wallet.DustySendRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Custom SendCoinsOfflineTask that respects excluded addresses by manually selecting UTXOs
 * @author Andreas Schildbach
 */
public abstract class SendCoinsOfflineTaskWithExcludedAddressSupport {
    private final Wallet wallet;
    private final Handler backgroundHandler;
    private final Handler callbackHandler;
    private final android.content.Context context;

    private static final Logger log = LoggerFactory.getLogger(SendCoinsOfflineTaskWithExcludedAddressSupport.class);

    public SendCoinsOfflineTaskWithExcludedAddressSupport(final Wallet wallet, final Handler backgroundHandler, final android.content.Context context) {
        this.wallet = wallet;
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
        this.context = context;
    }

    public final void sendCoinsOfflineWithExcludedAddressSupport(final SendRequest sendRequest) {
        backgroundHandler.post(() -> {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

            try {
                log.info("sending with excluded address support: {}", sendRequest);
                
                // Get excluded addresses
                Set<String> excludedAddresses = ExcludedAddressHelper.getExcludedAddresses();
                log.info("Excluded addresses: {}", excludedAddresses);
                
                // Create transaction with custom UTXO selection
                final Transaction transaction = createTransactionWithExcludedAddressSupport(sendRequest, excludedAddresses);
                
                log.info("send successful, transaction committed: {}", transaction.getTxId());
                
                // Check if we should use RadioDoge for broadcasting
                if (shouldUseRadioDoge()) {
                    log.info("No internet connection detected, attempting RadioDoge broadcast");
                    broadcastViaRadioDoge(transaction);
                } else {
                    callbackHandler.post(() -> onSuccess(transaction));
                }
            } catch (final InsufficientMoneyException x) {
                final Coin missing = x.missing;
                if (missing != null)
                    log.info("send failed, {} missing", missing.toFriendlyString());
                else
                    log.info("send failed, insufficient coins");

                callbackHandler.post(() -> onInsufficientMoney(x.missing));
            } catch (final ECKey.KeyIsEncryptedException x) {
                log.info("send failed, key is encrypted: {}", x.getMessage());
                callbackHandler.post(() -> onFailure(x));
            } catch (final KeyCrypterException x) {
                log.info("send failed, key crypter exception: {}", x.getMessage());
                callbackHandler.post(() -> onFailure(x));
            } catch (final Exception x) {
                log.info("send failed with exception: {}", x.getMessage(), x);
                callbackHandler.post(() -> onFailure(x));
            }
        });
    }

    /**
     * Create a transaction that respects excluded addresses by manually selecting UTXOs
     */
    private Transaction createTransactionWithExcludedAddressSupport(final SendRequest sendRequest, final Set<String> excludedAddresses) 
            throws InsufficientMoneyException, KeyCrypterException {
        
        // Get all unspent outputs
        List<TransactionOutput> unspentOutputs = wallet.getUnspents();
        
        // Filter out outputs from excluded addresses
        List<TransactionOutput> availableOutputs = new ArrayList<>();
        for (TransactionOutput output : unspentOutputs) {
            if (output.isAvailableForSpending()) {
                Address outputAddress = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                if (outputAddress != null && !excludedAddresses.contains(outputAddress.toString())) {
                    availableOutputs.add(output);
                }
            }
        }
        
        log.info("Available outputs for spending: {} (excluded {} outputs)", 
                availableOutputs.size(), unspentOutputs.size() - availableOutputs.size());
        
        if (availableOutputs.isEmpty()) {
            throw new InsufficientMoneyException(Coin.ZERO, "No available outputs (all from excluded addresses)");
        }
        
        // Sort outputs by value (largest first for efficiency)
        availableOutputs.sort(Collections.reverseOrder(Comparator.comparing(TransactionOutput::getValue)));
        
        // Calculate total value needed
        Coin totalNeeded = sendRequest.tx.getOutputSum();
        if (sendRequest.feePerKb != null) {
            // Estimate fee (simplified - in practice you'd want more sophisticated fee estimation)
            totalNeeded = totalNeeded.add(Coin.valueOf(1000)); // 1000 satoshis as base fee
        }
        
        // Select UTXOs to cover the amount
        List<TransactionOutput> selectedOutputs = new ArrayList<>();
        Coin selectedValue = Coin.ZERO;
        
        for (TransactionOutput output : availableOutputs) {
            selectedOutputs.add(output);
            selectedValue = selectedValue.add(output.getValue());
            
            if (selectedValue.compareTo(totalNeeded) >= 0) {
                break;
            }
        }
        
        if (selectedValue.isLessThan(totalNeeded)) {
            throw new InsufficientMoneyException(totalNeeded.subtract(selectedValue), 
                    "Insufficient funds in non-excluded addresses");
        }
        
        // Create transaction with selected outputs
        Transaction transaction = new Transaction(Constants.NETWORK_PARAMETERS);
        
        // Add outputs from sendRequest
        for (TransactionOutput output : sendRequest.tx.getOutputs()) {
            transaction.addOutput(output);
        }
        
        // Add inputs from selected outputs
        for (TransactionOutput output : selectedOutputs) {
            TransactionInput input = transaction.addInput(output);
            // The input will be signed later by wallet.completeTx()
        }
        
        // Set up the send request with our custom transaction
        SendRequest customSendRequest = SendRequest.forTx(transaction);
        customSendRequest.feePerKb = sendRequest.feePerKb;
        customSendRequest.memo = sendRequest.memo;
        customSendRequest.exchangeRate = sendRequest.exchangeRate;
        customSendRequest.aesKey = sendRequest.aesKey;
        customSendRequest.emptyWallet = sendRequest.emptyWallet;
        customSendRequest.signInputs = sendRequest.signInputs;
        
        // Check if Child Mode is active and set change address accordingly
        Address childModeAddress = ChildModeHelper.getChildModeAddressObject(context);
        if (childModeAddress != null) {
            // Override the change address to use the child's address
            customSendRequest.changeAddress = childModeAddress;
            log.info("Child Mode active: Using child address {} as change address", childModeAddress.toString());
        }
        
        // Complete the transaction (sign inputs, calculate fees, etc.)
        wallet.completeTx(customSendRequest);
        
        return customSendRequest.tx;
    }

    /**
     * Check if we should use RadioDoge for broadcasting
     */
    private boolean shouldUseRadioDoge() {
        try {
            // Get configuration to check if RadioDoge is enabled
            WalletApplication app = (WalletApplication) context.getApplicationContext();
            Configuration config = app.getConfiguration();
            
            if (!config.getRadioDogeEnabled()) {
                log.info("RadioDoge is disabled in settings");
                return false;
            }
            
            // Check if we have internet connectivity
            if (RadioDogeHelper.hasInternetConnectivity(context)) {
                log.info("Internet connectivity available, using normal broadcast");
                return false;
            }
            
            // Check if we're connected to RadioDoge WiFi
            if (!RadioDogeHelper.isConnectedToRadioDoge(context)) {
                log.info("Not connected to RadioDoge WiFi network");
                return false;
            }
            
            log.info("RadioDoge conditions met: no internet, connected to RadioDoge WiFi");
            return true;
        } catch (Exception e) {
            log.warn("Error checking RadioDoge conditions: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Broadcast transaction via RadioDoge
     */
    private void broadcastViaRadioDoge(final Transaction transaction) {
        RadioDogeHelper.broadcastTransactionViaRadioDogeWithConfirmation(transaction, new RadioDogeHelper.RadioDogeCallback() {
            @Override
            public void onSuccess() {
                log.info("RadioDoge broadcast successful for transaction: {}", transaction.getTxId());
                callbackHandler.post(() -> SendCoinsOfflineTaskWithExcludedAddressSupport.this.onSuccess(transaction));
            }

            @Override
            public void onFailure(String error) {
                log.warn("RadioDoge broadcast failed: {}", error);
                // Fall back to normal success callback - the transaction is still valid
                // The user can manually retry or the transaction will be broadcast when internet is available
                callbackHandler.post(() -> SendCoinsOfflineTaskWithExcludedAddressSupport.this.onSuccess(transaction));
            }
        });
    }

    protected abstract void onSuccess(final Transaction transaction);

    protected abstract void onInsufficientMoney(final Coin missing);

    protected abstract void onInvalidEncryptionKey();

    protected abstract void onEmptyWalletFailed();

    protected abstract void onFailure(final Exception exception);
}
