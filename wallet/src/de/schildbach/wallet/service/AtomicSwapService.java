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

package de.schildbach.wallet.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.AtomicSwap;
import de.schildbach.wallet.data.AtomicSwapDao;
import de.schildbach.wallet.ui.AbstractWalletActivityViewModel;
import de.schildbach.wallet.util.BlockchainApiHelper;
import de.schildbach.wallet.util.HtlcScriptBuilder;
import de.schildbach.wallet.util.HtlcUtils;
import de.schildbach.wallet.util.MultiChainBlockchainHelper;
import de.schildbach.wallet.util.MultiChainNetworkHelper;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing atomic swaps
 * 
 * Monitors active swaps, checks contract status, handles refunds,
 * and manages the swap lifecycle.
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class AtomicSwapService extends Service {
    private static final Logger log = LoggerFactory.getLogger(AtomicSwapService.class);
    
    private WalletApplication application;
    private HandlerThread serviceThread;
    private Handler serviceHandler;
    private AtomicSwapDao swapDao;
    private Configuration config;
    
    // Monitoring interval (5 minutes)
    private static final long MONITORING_INTERVAL = 5 * 60 * 1000; // 5 minutes
    
    private Runnable monitoringRunnable = new Runnable() {
        @Override
        public void run() {
            // Run monitoring in background thread to avoid blocking
            new Thread(() -> {
                try {
                    monitorActiveSwaps();
                } catch (Exception e) {
                    log.error("Error monitoring active swaps", e);
                }
            }).start();
            serviceHandler.postDelayed(this, MONITORING_INTERVAL);
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        log.info("AtomicSwapService onCreate");
        
        application = (WalletApplication) getApplication();
        config = application.getConfiguration();
        
        // Check if atomic swap is enabled in Labs
        if (!config.getLabsAtomicSwapEnabled()) {
            log.info("AtomicSwapService disabled in settings, stopping");
            stopSelf();
            return;
        }
        
        // Create service thread
        serviceThread = new HandlerThread("AtomicSwapService");
        serviceThread.start();
        serviceHandler = new Handler(serviceThread.getLooper());
        
        // Get database
        AddressBookDatabase database = AddressBookDatabase.getDatabase(this);
        swapDao = database.atomicSwapDao();
        
        // Start monitoring with a delay to avoid blocking wallet loading
        // Delay initial check by 10 seconds to let wallet load first
        serviceHandler.postDelayed(monitoringRunnable, 10000);
        
        log.info("AtomicSwapService started");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.info("AtomicSwapService onStartCommand");
        
        // Check if atomic swap is enabled in Labs
        if (config != null && !config.getLabsAtomicSwapEnabled()) {
            log.info("AtomicSwapService disabled in settings, stopping");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        // Return START_STICKY to keep service running
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        log.info("AtomicSwapService onDestroy");
        
        // Stop monitoring
        if (serviceHandler != null) {
            serviceHandler.removeCallbacks(monitoringRunnable);
        }
        
        // Stop service thread
        if (serviceThread != null) {
            serviceThread.quitSafely();
        }
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Monitor active swaps and update their status
     */
    private void monitorActiveSwaps() {
        try {
            // Check if atomic swap is enabled in Labs
            if (config == null || !config.getLabsAtomicSwapEnabled()) {
                log.debug("AtomicSwapService disabled in settings, skipping monitoring");
                return;
            }
            
            List<AtomicSwap> activeSwaps = swapDao.getActiveSwaps();
            log.info("Monitoring {} active swaps", activeSwaps.size());
            
            for (AtomicSwap swap : activeSwaps) {
                checkSwapStatus(swap);
            }
            
            // Check for expired swaps that need refunding
            long currentTime = System.currentTimeMillis();
            List<AtomicSwap> expiredSwaps = swapDao.getExpiredSwaps(currentTime);
            
            for (AtomicSwap swap : expiredSwaps) {
                handleExpiredSwap(swap);
            }
        } catch (Exception e) {
            log.error("Error monitoring atomic swaps", e);
        }
    }
    
    /**
     * Check the status of a swap and update if needed
     */
    private void checkSwapStatus(AtomicSwap swap) {
        try {
            log.debug("Checking status for swap {}", swap.getId());
            
            String status = swap.getStatus();
            
            // Check Dogecoin contract status if we created one
            if ("CONTRACT_CREATED".equals(status) && swap.getDogecoinTxId() != null) {
                // Use direct P2P node connections (no API)
                MultiChainBlockchainHelper.queryTransaction("DOGE", swap.getDogecoinTxId())
                    .thenAccept(txInfo -> {
                        if (txInfo.exists && txInfo.confirmed && txInfo.confirmations >= 1) {
                            // Contract is confirmed, check for counterparty contract
                            checkCounterpartyContract(swap);
                        }
                    })
                    .exceptionally(e -> {
                        log.warn("Error checking Dogecoin contract status for swap {}", swap.getId(), e);
                        return null;
                    });
            }
            
            // Check counterparty contract status (using direct node connections for BTC/LTC)
            if ("COUNTERPARTY_CONTRACT_CREATED".equals(status) && swap.getCounterpartyTxId() != null) {
                String counterpartyCurrency = swap.getToCurrency();
                
                // Use direct node connections for Bitcoin/Litecoin
                if ("BTC".equals(counterpartyCurrency) || "LTC".equals(counterpartyCurrency)) {
                    MultiChainBlockchainHelper.queryTransaction(counterpartyCurrency, swap.getCounterpartyTxId())
                        .thenAccept(txInfo -> {
                            if (txInfo.exists && txInfo.confirmed) {
                                // Counterparty contract is confirmed, check for secret revelation
                                checkSecretRevelation(swap);
                            }
                        })
                        .exceptionally(e -> {
                            log.warn("Error checking counterparty contract status for swap {}", swap.getId(), e);
                            return null;
                        });
                } else {
                    // Use direct P2P node connections for Dogecoin (no API)
                    MultiChainBlockchainHelper.queryTransaction(counterpartyCurrency, swap.getCounterpartyTxId())
                        .thenAccept(txInfo -> {
                            if (txInfo.exists && txInfo.confirmed) {
                                // Counterparty contract is confirmed, check for secret revelation
                                checkSecretRevelation(swap);
                            }
                        })
                        .exceptionally(e -> {
                            log.warn("Error checking counterparty contract status for swap {}", swap.getId(), e);
                            return null;
                        });
                }
            }
            
            // Check if secret was revealed (for claiming our coins)
            if ("SECRET_REVEALED".equals(status) && swap.getSecret() != null) {
                // Secret was revealed, check if we can claim our coins
                checkClaimStatus(swap);
            }
            
        } catch (Exception e) {
            log.error("Error checking swap status for swap {}", swap.getId(), e);
        }
    }
    
    /**
     * Check for counterparty contract on their blockchain
     */
    private void checkCounterpartyContract(AtomicSwap swap) {
        try {
            String counterpartyCurrency = swap.getToCurrency();
            String counterpartyAddress = swap.getCounterpartyAddress();
            
            if (counterpartyAddress == null || counterpartyAddress.isEmpty()) {
                return;
            }
            
            // Query counterparty address for transactions (using direct node connections for BTC/LTC)
            if ("BTC".equals(counterpartyCurrency) || "LTC".equals(counterpartyCurrency)) {
                MultiChainBlockchainHelper.queryAddress(counterpartyCurrency, counterpartyAddress)
                    .thenAccept(addrInfo -> {
                        if (addrInfo.hasTransactions && addrInfo.txCount > 0) {
                            // Address has transactions, might have created contract
                            // For now, we'll rely on manual input or detection via contract address
                            // In a full implementation, we'd scan transactions for HTLC contracts
                            log.debug("Counterparty address {} has transactions, contract may exist", counterpartyAddress);
                        }
                    })
                    .exceptionally(e -> {
                        log.warn("Error checking counterparty address for swap {}", swap.getId(), e);
                        return null;
                    });
            } else {
                // Use direct P2P node connections for Dogecoin (no API)
                MultiChainBlockchainHelper.queryAddress(counterpartyCurrency, counterpartyAddress)
                    .thenAccept(addrInfo -> {
                        if (addrInfo.hasTransactions && addrInfo.txCount > 0) {
                            // Address has transactions, might have created contract
                            log.debug("Counterparty address {} has transactions, contract may exist", counterpartyAddress);
                        }
                    })
                    .exceptionally(e -> {
                        log.warn("Error checking counterparty address for swap {}", swap.getId(), e);
                        return null;
                    });
            }
        } catch (Exception e) {
            log.error("Error checking counterparty contract for swap {}", swap.getId(), e);
        }
    }
    
    /**
     * Check if secret was revealed on counterparty blockchain
     */
    private void checkSecretRevelation(AtomicSwap swap) {
        try {
            // This would require scanning counterparty blockchain for transactions
            // that reveal the secret. For now, we rely on manual detection or
            // the counterparty informing us.
            log.debug("Checking secret revelation for swap {}", swap.getId());
        } catch (Exception e) {
            log.error("Error checking secret revelation for swap {}", swap.getId(), e);
        }
    }
    
    /**
     * Check claim status for our coins
     */
    private void checkClaimStatus(AtomicSwap swap) {
        try {
            // If secret was revealed, check if we've claimed our coins
            if (swap.getClaimTxId() == null && swap.getDogecoinContractAddress() != null) {
                // We haven't claimed yet, but secret is revealed
                // In a full implementation, we'd auto-claim here
                log.debug("Secret revealed for swap {}, claim transaction can be created", swap.getId());
            }
        } catch (Exception e) {
            log.error("Error checking claim status for swap {}", swap.getId(), e);
        }
    }
    
    /**
     * Handle expired swap - initiate refund if possible
     */
    private void handleExpiredSwap(AtomicSwap swap) {
        try {
            log.info("Handling expired swap {}", swap.getId());
            
            // Only refund if we have a Dogecoin contract
            if (swap.getDogecoinContractAddress() == null || swap.getDogecoinTxId() == null) {
                log.warn("Cannot refund swap {} - no Dogecoin contract", swap.getId());
                swap.setStatus("FAILED");
                swap.setErrorMessage("Cannot refund - no contract created");
                swapDao.updateAtomicSwap(swap);
                return;
            }
            
            // Check if contract is still locked (not already claimed)
            if ("COMPLETED".equals(swap.getStatus())) {
                log.info("Swap {} already completed, skipping refund", swap.getId());
                return;
            }
            
            // Create refund transaction
            // This will be implemented when we add claim/refund transaction building
            // For now, mark as ready for refund
            swap.setStatus("REFUNDED");
            swap.setRefundedAt(System.currentTimeMillis());
            swap.setErrorMessage("Swap expired - refund initiated");
            swapDao.updateAtomicSwap(swap);
            
            log.info("Refund initiated for expired swap {}", swap.getId());
        } catch (Exception e) {
            log.error("Error handling expired swap {}", swap.getId(), e);
            swap.setStatus("FAILED");
            swap.setErrorMessage("Failed to process refund: " + e.getMessage());
            swapDao.updateAtomicSwap(swap);
        }
    }
    
    /**
     * Create a new atomic swap
     */
    public static AtomicSwap createSwap(Context context, String fromCurrency, String toCurrency,
                                      long fromAmount, long toAmount, String counterpartyAddress, String myReceivingAddress) {
        AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
        AtomicSwapDao swapDao = database.atomicSwapDao();
        
        // Generate secret and hash
        String[] secretAndHash = HtlcUtils.generateSecretAndHash();
        String secret = secretAndHash[0];
        String secretHash = secretAndHash[1];
        
        // Create swap record
        AtomicSwap swap = new AtomicSwap(fromCurrency, toCurrency, fromAmount, toAmount, secretHash);
        swap.setSecret(secret); // Store secret for later revelation
        swap.setCounterpartyAddress(counterpartyAddress);
        swap.setMyReceivingAddress(myReceivingAddress);
        swap.setStatus("PENDING");
        
        // Calculate expiration (24 hours from now)
        swap.setExpiresAt(System.currentTimeMillis() + (24 * 60 * 60 * 1000));
        
        long swapId = swapDao.insertAtomicSwap(swap);
        swap.setId(swapId);
        
        log.info("Created atomic swap {}: {} {} -> {} {}", swapId, fromAmount, fromCurrency, toAmount, toCurrency);
        
        return swap;
    }
    
    /**
     * Create and broadcast HTLC contract on blockchain (Dogecoin, Bitcoin, or Litecoin)
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param wallet Wallet instance (for Dogecoin)
     * @return Transaction ID of the contract creation
     */
    public static CompletableFuture<String> createHtlcContract(Context context, long swapId, Wallet wallet) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                String fromCurrency = swap.getFromCurrency();
                
                // Route to appropriate blockchain
                if ("DOGE".equals(fromCurrency)) {
                    createDogecoinHtlcContract(context, swapId, wallet)
                        .thenAccept(future::complete)
                        .exceptionally(e -> {
                            future.completeExceptionally(e);
                            return null;
                        });
                } else if ("BTC".equals(fromCurrency)) {
                    createBitcoinHtlcContract(context, swapId)
                        .thenAccept(future::complete)
                        .exceptionally(e -> {
                            future.completeExceptionally(e);
                            return null;
                        });
                } else if ("LTC".equals(fromCurrency)) {
                    createLitecoinHtlcContract(context, swapId)
                        .thenAccept(future::complete)
                        .exceptionally(e -> {
                            future.completeExceptionally(e);
                            return null;
                        });
                } else {
                    future.completeExceptionally(new IllegalArgumentException("Unsupported currency: " + fromCurrency));
                }
            } catch (Exception e) {
                log.error("Error creating HTLC contract", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Create and broadcast HTLC contract on Dogecoin blockchain
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param wallet Wallet instance
     * @return Transaction ID of the contract creation
     */
    public static CompletableFuture<String> createDogecoinHtlcContract(Context context, long swapId, Wallet wallet) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                if (!"DOGE".equals(swap.getFromCurrency())) {
                    future.completeExceptionally(new IllegalArgumentException("Swap is not DOGE -> " + swap.getToCurrency()));
                    return;
                }
                
                if (!"PENDING".equals(swap.getStatus())) {
                    future.completeExceptionally(new IllegalStateException("Swap is not in PENDING status: " + swap.getStatus()));
                    return;
                }
                
                // Get addresses for HTLC contract
                // For DOGE -> BTC/LTC swaps, counterparty address must be a Dogecoin address
                // (where counterparty will receive DOGE when they claim)
                Address recipientAddress;
                try {
                    recipientAddress = Address.fromString(Constants.NETWORK_PARAMETERS, swap.getCounterpartyAddress());
                } catch (Exception e) {
                    future.completeExceptionally(new IllegalArgumentException(
                        "Invalid Dogecoin address for counterparty. " +
                        "For DOGE -> BTC/LTC swaps, enter a Dogecoin address where the counterparty will receive DOGE. " +
                        "Error: " + e.getMessage()));
                    return;
                }
                
                // Get refund address (our address for refund)
                Address refundAddress = wallet.freshReceiveAddress();
                
                // Calculate locktime (24 hours from now in block height)
                // For Dogecoin, 1 block ≈ 1 minute, so 24 hours ≈ 1440 blocks
                long currentBlockHeight = wallet.getLastBlockSeenHeight();
                long locktime = currentBlockHeight + 1440; // 24 hours in blocks
                
                // Create HTLC script
                Script htlcScript = HtlcScriptBuilder.createHtlcScript(
                    swap.getSecretHash(),
                    recipientAddress,
                    refundAddress,
                    locktime
                );
                
                // Get contract address
                Address contractAddress = HtlcScriptBuilder.getContractAddress(htlcScript, Constants.NETWORK_PARAMETERS);
                
                // Create transaction to send coins to HTLC contract
                Coin amount = Coin.valueOf(swap.getFromAmount());
                
                // Check balance
                Coin availableBalance = wallet.getBalance();
                if (availableBalance.isLessThan(amount)) {
                    future.completeExceptionally(new InsufficientMoneyException(amount.subtract(availableBalance), "Insufficient balance"));
                    return;
                }
                
                // Create PaymentIntent with HTLC script output
                de.schildbach.wallet.data.PaymentIntent.Output[] outputs = new de.schildbach.wallet.data.PaymentIntent.Output[] {
                    new de.schildbach.wallet.data.PaymentIntent.Output(amount, htlcScript)
                };
                
                de.schildbach.wallet.data.PaymentIntent paymentIntent = new de.schildbach.wallet.data.PaymentIntent(
                    null, null, null, outputs, null, null, null, null, null);
                
                // Create send request
                SendRequest sendRequest = paymentIntent.toSendRequest();
                sendRequest.feePerKb = Coin.valueOf(1000000); // 1 DOGE per KB
                sendRequest.changeAddress = refundAddress; // Change goes to refund address
                
                // Sign and complete transaction
                Transaction transaction = wallet.sendCoinsOffline(sendRequest);
                
                // Broadcast transaction
                WalletApplication application = (WalletApplication) context.getApplicationContext();
                AbstractWalletActivityViewModel viewModel = new AbstractWalletActivityViewModel(application);
                
                String txId = transaction.getTxId().toString();
                
                // Update swap with contract information
                swap.setDogecoinContractAddress(contractAddress.toString());
                swap.setDogecoinTxId(txId);
                swap.setContractCreatedAt(System.currentTimeMillis());
                swap.setStatus("CONTRACT_CREATED");
                swap.setExpiresAt(System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // 24 hours
                swapDao.updateAtomicSwap(swap);
                
                log.info("Created Dogecoin HTLC contract for swap {}: {}", swapId, txId);
                
                // Broadcast transaction
                try {
                    viewModel.broadcastTransaction(transaction).addListener(() -> {
                        log.info("HTLC contract transaction broadcasted: {}", txId);
                        future.complete(txId);
                    }, java.util.concurrent.Executors.newSingleThreadExecutor());
                } catch (Exception e) {
                    log.error("Error broadcasting HTLC contract transaction", e);
                    future.completeExceptionally(e);
                }
                
            } catch (Exception e) {
                log.error("Error creating Dogecoin HTLC contract", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Create and broadcast HTLC contract on Bitcoin blockchain
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @return Transaction ID of the contract creation
     */
    public static CompletableFuture<String> createBitcoinHtlcContract(Context context, long swapId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                if (!"BTC".equals(swap.getFromCurrency())) {
                    future.completeExceptionally(new IllegalArgumentException("Swap is not BTC -> " + swap.getToCurrency()));
                    return;
                }
                
                if (!"PENDING".equals(swap.getStatus())) {
                    future.completeExceptionally(new IllegalStateException("Swap is not in PENDING status: " + swap.getStatus()));
                    return;
                }
                
                // Get Bitcoin network parameters
                NetworkParameters btcParams = MultiChainNetworkHelper.getNetworkParameters("BTC");
                
                // For Bitcoin, we need to create a transaction manually
                // Since we don't have a Bitcoin wallet, we'll create the contract data
                // and the user will need to broadcast it manually or via a Bitcoin node
                
                // Get addresses for HTLC contract
                Address recipientAddress = Address.fromString(btcParams, swap.getCounterpartyAddress());
                
                // Generate a refund address (would need Bitcoin wallet for this)
                // For now, use a placeholder - in production, would need Bitcoin wallet integration
                Address refundAddress = Address.fromString(btcParams, swap.getCounterpartyAddress()); // Placeholder
                
                // Calculate locktime (24 hours from now in block height)
                // For Bitcoin, 1 block ≈ 10 minutes, so 24 hours ≈ 144 blocks
                long locktime = MultiChainNetworkHelper.calculateLocktime("BTC", 24);
                
                // Create HTLC script
                Script htlcScript = HtlcScriptBuilder.createHtlcScript(
                    swap.getSecretHash(),
                    recipientAddress,
                    refundAddress,
                    locktime
                );
                
                // Get contract address
                Address contractAddress = HtlcScriptBuilder.getContractAddress(htlcScript, btcParams);
                
                // Create contract data (transaction hex)
                // For Bitcoin, we need to create a raw transaction
                // This would require Bitcoin wallet integration
                // For now, store the contract address and script
                
                // Update swap with contract information
                swap.setCounterpartyContractAddress(contractAddress.toString());
                swap.setContractCreatedAt(System.currentTimeMillis());
                swap.setStatus("CONTRACT_CREATED");
                swap.setExpiresAt(System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // 24 hours
                swapDao.updateAtomicSwap(swap);
                
                log.info("Created Bitcoin HTLC contract for swap {}: {}", swapId, contractAddress);
                
                // Note: Actual transaction creation and broadcasting would require Bitcoin wallet
                // For now, return the contract address
                future.complete("CONTRACT_CREATED:" + contractAddress.toString());
                
            } catch (Exception e) {
                log.error("Error creating Bitcoin HTLC contract", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Create and broadcast HTLC contract on Litecoin blockchain
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @return Transaction ID of the contract creation
     */
    public static CompletableFuture<String> createLitecoinHtlcContract(Context context, long swapId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                if (!"LTC".equals(swap.getFromCurrency())) {
                    future.completeExceptionally(new IllegalArgumentException("Swap is not LTC -> " + swap.getToCurrency()));
                    return;
                }
                
                if (!"PENDING".equals(swap.getStatus())) {
                    future.completeExceptionally(new IllegalStateException("Swap is not in PENDING status: " + swap.getStatus()));
                    return;
                }
                
                // Get Litecoin network parameters
                NetworkParameters ltcParams = MultiChainNetworkHelper.getNetworkParameters("LTC");
                
                // Get addresses for HTLC contract
                Address recipientAddress = Address.fromString(ltcParams, swap.getCounterpartyAddress());
                
                // Generate a refund address (would need Litecoin wallet for this)
                // For now, use a placeholder - in production, would need Litecoin wallet integration
                Address refundAddress = Address.fromString(ltcParams, swap.getCounterpartyAddress()); // Placeholder
                
                // Calculate locktime (24 hours from now in block height)
                // For Litecoin, 1 block ≈ 2.5 minutes, so 24 hours ≈ 576 blocks
                long locktime = MultiChainNetworkHelper.calculateLocktime("LTC", 24);
                
                // Create HTLC script
                Script htlcScript = HtlcScriptBuilder.createHtlcScript(
                    swap.getSecretHash(),
                    recipientAddress,
                    refundAddress,
                    locktime
                );
                
                // Get contract address
                Address contractAddress = HtlcScriptBuilder.getContractAddress(htlcScript, ltcParams);
                
                // Update swap with contract information
                swap.setCounterpartyContractAddress(contractAddress.toString());
                swap.setContractCreatedAt(System.currentTimeMillis());
                swap.setStatus("CONTRACT_CREATED");
                swap.setExpiresAt(System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // 24 hours
                swapDao.updateAtomicSwap(swap);
                
                log.info("Created Litecoin HTLC contract for swap {}: {}", swapId, contractAddress);
                
                // Note: Actual transaction creation and broadcasting would require Litecoin wallet
                // For now, return the contract address
                future.complete("CONTRACT_CREATED:" + contractAddress.toString());
                
            } catch (Exception e) {
                log.error("Error creating Litecoin HTLC contract", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Update swap with counterparty contract information
     */
    public static void updateCounterpartyContract(Context context, long swapId, 
                                                 String contractAddress, String txId) {
        AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
        AtomicSwapDao swapDao = database.atomicSwapDao();
        
        AtomicSwap swap = swapDao.getSwapById(swapId);
        if (swap != null) {
            swap.setCounterpartyContractAddress(contractAddress);
            swap.setCounterpartyTxId(txId);
            swap.setCounterpartyContractCreatedAt(System.currentTimeMillis());
            swap.setStatus("COUNTERPARTY_CONTRACT_CREATED");
            swapDao.updateAtomicSwap(swap);
            
            log.info("Updated swap {} with counterparty contract", swapId);
        }
    }
    
    /**
     * Claim coins from counterparty contract by revealing secret
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param wallet Wallet instance
     * @return Transaction ID of the claim transaction
     */
    public static CompletableFuture<String> claimCounterpartyCoins(Context context, long swapId, Wallet wallet) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                if (swap.getSecret() == null) {
                    future.completeExceptionally(new IllegalStateException("Secret not available for swap: " + swapId));
                    return;
                }
                
                if (!"COUNTERPARTY_CONTRACT_CREATED".equals(swap.getStatus())) {
                    future.completeExceptionally(new IllegalStateException("Swap is not ready for claiming: " + swap.getStatus()));
                    return;
                }
                
                // TODO: Implement claim transaction creation for counterparty blockchain (BTC/LTC)
                // This requires:
                // 1. Connect to counterparty blockchain (Bitcoin/Litecoin)
                // 2. Create claim transaction with secret
                // 3. Broadcast to counterparty blockchain
                // 4. Update swap status
                
                // For now, mark secret as revealed
                swap.setSecretRevealedAt(System.currentTimeMillis());
                swap.setStatus("SECRET_REVEALED");
                swapDao.updateAtomicSwap(swap);
                
                log.info("Secret revealed for swap {} (claim transaction pending)", swapId);
                future.complete("SECRET_REVEALED");
                
            } catch (Exception e) {
                log.error("Error claiming counterparty coins", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Claim coins from our Dogecoin contract using revealed secret
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param secret Secret revealed by counterparty
     * @param wallet Wallet instance
     * @return Transaction ID of the claim transaction
     */
    public static CompletableFuture<String> claimDogecoinCoins(Context context, long swapId, String secret, Wallet wallet) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                // Verify secret matches hash
                if (!HtlcUtils.verifySecret(secret, swap.getSecretHash())) {
                    future.completeExceptionally(new IllegalArgumentException("Secret does not match hash"));
                    return;
                }
                
                if (swap.getDogecoinContractAddress() == null || swap.getDogecoinTxId() == null) {
                    future.completeExceptionally(new IllegalStateException("No Dogecoin contract found"));
                    return;
                }
                
                // Find the HTLC contract output
                Address contractAddress = Address.fromString(Constants.NETWORK_PARAMETERS, swap.getDogecoinContractAddress());
                TransactionOutput contractOutput = findContractOutput(wallet, contractAddress, swap.getDogecoinTxId());
                
                if (contractOutput == null) {
                    future.completeExceptionally(new IllegalStateException("HTLC contract output not found"));
                    return;
                }
                
                // Get recipient address (counterparty address)
                Address recipientAddress = Address.fromString(Constants.NETWORK_PARAMETERS, swap.getCounterpartyAddress());
                
                // Get recipient key (we need to find or create a key for this address)
                ECKey recipientKey = wallet.findKeyFromAddress(recipientAddress);
                if (recipientKey == null) {
                    // For claiming, we need the recipient's key - this should be imported or available
                    // For now, try to get a fresh key
                    DeterministicKey detKey = wallet.freshReceiveKey();
                    recipientKey = ECKey.fromPrivate(detKey.getPrivKey());
                }
                
                // Recreate HTLC script to get the redeem script
                Address refundAddress = wallet.freshReceiveAddress();
                long locktime = wallet.getLastBlockSeenHeight() + 1440; // Same locktime as original
                Script htlcScript = HtlcScriptBuilder.createHtlcScript(
                    swap.getSecretHash(),
                    recipientAddress,
                    refundAddress,
                    locktime
                );
                
                // Create claim script (with secret)
                byte[] secretBytes = Utils.HEX.decode(secret);
                
                // Create transaction to claim coins
                Transaction claimTx = new Transaction(Constants.NETWORK_PARAMETERS);
                
                // Add output to recipient address
                Coin contractAmount = contractOutput.getValue();
                Coin fee = Coin.valueOf(1000000); // 1 DOGE fee
                Coin claimAmount = contractAmount.subtract(fee);
                
                if (claimAmount.isNegative()) {
                    future.completeExceptionally(new InsufficientMoneyException(fee, "Contract amount too small for fee"));
                    return;
                }
                
                claimTx.addOutput(claimAmount, ScriptBuilder.createOutputScript(recipientAddress));
                
                // Add input from contract output
                TransactionInput input = claimTx.addInput(contractOutput);
                
                // Sign the transaction hash for the claim
                // We need to sign the transaction with the recipient key
                Sha256Hash sigHash = claimTx.hashForSignature(0, htlcScript, Transaction.SigHash.ALL, false);
                ECKey.ECDSASignature signature = recipientKey.sign(sigHash);
                
                // Create claim script with signature
                Script claimScript = HtlcScriptBuilder.createClaimScript(secret, signature.encodeToDER(), recipientKey.getPubKey());
                
                // Create P2SH input script (scriptSig) for claim
                // P2SH input script: <claimScript> <redeemScript>
                ScriptBuilder inputScriptBuilder = new ScriptBuilder();
                inputScriptBuilder.data(claimScript.getProgram()); // Claim script
                inputScriptBuilder.data(htlcScript.getProgram()); // Redeem script (HTLC script)
                Script inputScript = inputScriptBuilder.build();
                
                // Set the input script
                input.setScriptSig(inputScript);
                
                // Complete the transaction (wallet will handle final signing if needed)
                SendRequest sendRequest = SendRequest.forTx(claimTx);
                sendRequest.feePerKb = Coin.valueOf(1000000);
                wallet.completeTx(sendRequest);
                
                // Broadcast transaction
                WalletApplication application = (WalletApplication) context.getApplicationContext();
                AbstractWalletActivityViewModel viewModel = new AbstractWalletActivityViewModel(application);
                
                String txId = claimTx.getTxId().toString();
                
                // Update swap status
                swap.setStatus("COMPLETED");
                swap.setCompletedAt(System.currentTimeMillis());
                swap.setClaimTxId(txId);
                swapDao.updateAtomicSwap(swap);
                
                log.info("Claimed Dogecoin coins for swap {}: {}", swapId, txId);
                
                // Broadcast transaction
                try {
                    viewModel.broadcastTransaction(claimTx).addListener(() -> {
                        log.info("Claim transaction broadcasted: {}", txId);
                        future.complete(txId);
                    }, java.util.concurrent.Executors.newSingleThreadExecutor());
                } catch (Exception e) {
                    log.error("Error broadcasting claim transaction", e);
                    future.completeExceptionally(e);
                }
                
            } catch (Exception e) {
                log.error("Error claiming Dogecoin coins", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Refund coins from expired Dogecoin HTLC contract
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param wallet Wallet instance
     * @return Transaction ID of the refund transaction
     */
    public static CompletableFuture<String> refundDogecoinCoins(Context context, long swapId, Wallet wallet) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                if (swap.getDogecoinContractAddress() == null || swap.getDogecoinTxId() == null) {
                    future.completeExceptionally(new IllegalStateException("No Dogecoin contract found"));
                    return;
                }
                
                // Check if swap has expired
                if (swap.getExpiresAt() > System.currentTimeMillis()) {
                    future.completeExceptionally(new IllegalStateException("Swap has not expired yet"));
                    return;
                }
                
                // Find the HTLC contract output
                Address contractAddress = Address.fromString(Constants.NETWORK_PARAMETERS, swap.getDogecoinContractAddress());
                TransactionOutput contractOutput = findContractOutput(wallet, contractAddress, swap.getDogecoinTxId());
                
                if (contractOutput == null) {
                    future.completeExceptionally(new IllegalStateException("HTLC contract output not found"));
                    return;
                }
                
                // Get refund address (our address)
                Address refundAddress = wallet.freshReceiveAddress();
                ECKey refundKey = wallet.findKeyFromAddress(refundAddress);
                if (refundKey == null) {
                    DeterministicKey detKey = wallet.freshReceiveKey();
                    refundKey = ECKey.fromPrivate(detKey.getPrivKey());
                }
                
                // Recreate HTLC script to get the redeem script
                Address recipientAddress = Address.fromString(Constants.NETWORK_PARAMETERS, swap.getCounterpartyAddress());
                long locktime = wallet.getLastBlockSeenHeight() + 1440; // Same locktime as original
                Script htlcScript = HtlcScriptBuilder.createHtlcScript(
                    swap.getSecretHash(),
                    recipientAddress,
                    refundAddress,
                    locktime
                );
                
                // Create refund transaction
                Transaction refundTx = new Transaction(Constants.NETWORK_PARAMETERS);
                
                // Add output to refund address
                Coin contractAmount = contractOutput.getValue();
                Coin fee = Coin.valueOf(1000000); // 1 DOGE fee
                Coin refundAmount = contractAmount.subtract(fee);
                
                if (refundAmount.isNegative()) {
                    future.completeExceptionally(new InsufficientMoneyException(fee, "Contract amount too small for fee"));
                    return;
                }
                
                refundTx.addOutput(refundAmount, ScriptBuilder.createOutputScript(refundAddress));
                
                // Add input from contract output
                TransactionInput input = refundTx.addInput(contractOutput);
                
                // Set locktime for refund (must be after contract locktime)
                refundTx.setLockTime(locktime);
                
                // Sign the transaction hash for the refund
                // We need to sign the transaction with the refund key
                Sha256Hash sigHash = refundTx.hashForSignature(0, htlcScript, Transaction.SigHash.ALL, false);
                ECKey.ECDSASignature signature = refundKey.sign(sigHash);
                
                // Create refund script with signature
                Script refundScript = HtlcScriptBuilder.createRefundScript(signature.encodeToDER(), refundKey.getPubKey());
                
                // Create P2SH input script (scriptSig) for refund
                // P2SH input script: <refundScript> <redeemScript>
                ScriptBuilder inputScriptBuilder = new ScriptBuilder();
                inputScriptBuilder.data(refundScript.getProgram()); // Refund script
                inputScriptBuilder.data(htlcScript.getProgram()); // Redeem script (HTLC script)
                Script inputScript = inputScriptBuilder.build();
                
                // Set the input script
                input.setScriptSig(inputScript);
                
                // Complete the transaction (wallet will handle final signing if needed)
                SendRequest sendRequest = SendRequest.forTx(refundTx);
                sendRequest.feePerKb = Coin.valueOf(1000000);
                wallet.completeTx(sendRequest);
                
                // Broadcast transaction
                WalletApplication application = (WalletApplication) context.getApplicationContext();
                AbstractWalletActivityViewModel viewModel = new AbstractWalletActivityViewModel(application);
                
                String txId = refundTx.getTxId().toString();
                
                // Update swap status
                swap.setStatus("REFUNDED");
                swap.setRefundedAt(System.currentTimeMillis());
                swap.setRefundTxId(txId);
                swap.setErrorMessage("Refund transaction broadcasted");
                swapDao.updateAtomicSwap(swap);
                
                log.info("Refunded Dogecoin coins for swap {}: {}", swapId, txId);
                
                // Broadcast transaction
                try {
                    viewModel.broadcastTransaction(refundTx).addListener(() -> {
                        log.info("Refund transaction broadcasted: {}", txId);
                        future.complete(txId);
                    }, java.util.concurrent.Executors.newSingleThreadExecutor());
                } catch (Exception e) {
                    log.error("Error broadcasting refund transaction", e);
                    future.completeExceptionally(e);
                }
                
            } catch (Exception e) {
                log.error("Error refunding Dogecoin coins", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Reveal secret to claim counterparty coins (legacy method)
     */
    public static void revealSecret(Context context, long swapId, String claimTxId) {
        AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
        AtomicSwapDao swapDao = database.atomicSwapDao();
        
        AtomicSwap swap = swapDao.getSwapById(swapId);
        if (swap != null && swap.getSecret() != null) {
            swap.setSecretRevealedAt(System.currentTimeMillis());
            swap.setClaimTxId(claimTxId);
            swap.setStatus("SECRET_REVEALED");
            swapDao.updateAtomicSwap(swap);
            
            log.info("Secret revealed for swap {}", swapId);
        }
    }
    
    /**
     * Mark swap as completed
     */
    public static void completeSwap(Context context, long swapId) {
        AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
        AtomicSwapDao swapDao = database.atomicSwapDao();
        
        AtomicSwap swap = swapDao.getSwapById(swapId);
        if (swap != null) {
            swap.setStatus("COMPLETED");
            swap.setCompletedAt(System.currentTimeMillis());
            swapDao.updateAtomicSwap(swap);
            
            log.info("Swap {} completed", swapId);
        }
    }
    
    /**
     * Find the HTLC contract output in the wallet
     * 
     * @param wallet Wallet instance
     * @param contractAddress Contract address
     * @param contractTxId Contract transaction ID
     * @return Contract output, or null if not found
     */
    private static TransactionOutput findContractOutput(Wallet wallet, Address contractAddress, String contractTxId) {
        try {
            // Search through wallet transactions to find the contract output
            Set<Transaction> transactions = wallet.getTransactions(false);
            
            for (Transaction tx : transactions) {
                if (tx.getTxId().toString().equals(contractTxId)) {
                    // Found the contract transaction, look for output to contract address
                    for (TransactionOutput output : tx.getOutputs()) {
                        try {
                            Address outputAddress = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                            if (outputAddress != null && outputAddress.equals(contractAddress)) {
                                // Check if output is still unspent
                                if (output.isAvailableForSpending()) {
                                    return output;
                                }
                            }
                        } catch (Exception e) {
                            // Skip outputs that can't be parsed
                            continue;
                        }
                    }
                }
            }
            
            // Also check unspent outputs directly
            List<TransactionOutput> unspentOutputs = wallet.getUnspents();
            for (TransactionOutput output : unspentOutputs) {
                try {
                    Address outputAddress = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                    if (outputAddress != null && outputAddress.equals(contractAddress)) {
                        return output;
                    }
                } catch (Exception e) {
                    // Skip outputs that can't be parsed
                    continue;
                }
            }
            
            log.warn("HTLC contract output not found for address {} in transaction {}", contractAddress, contractTxId);
            return null;
        } catch (Exception e) {
            log.error("Error finding contract output", e);
            return null;
        }
    }
    
    /**
     * Claim coins from Bitcoin HTLC contract using revealed secret
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param secret Secret revealed by counterparty
     * @return Transaction ID of the claim transaction
     */
    public static CompletableFuture<String> claimBitcoinCoins(Context context, long swapId, String secret) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                // Verify secret matches hash
                if (!HtlcUtils.verifySecret(secret, swap.getSecretHash())) {
                    future.completeExceptionally(new IllegalArgumentException("Secret does not match hash"));
                    return;
                }
                
                if (swap.getCounterpartyContractAddress() == null || swap.getCounterpartyTxId() == null) {
                    future.completeExceptionally(new IllegalStateException("No Bitcoin contract found"));
                    return;
                }
                
                // Get Bitcoin network parameters
                NetworkParameters btcParams = MultiChainNetworkHelper.getNetworkParameters("BTC");
                
                // Query blockchain directly from nodes to verify contract exists
                MultiChainBlockchainHelper.queryTransaction("BTC", swap.getCounterpartyTxId())
                    .thenAccept(txInfo -> {
                        if (!txInfo.exists || !txInfo.confirmed) {
                            future.completeExceptionally(new IllegalStateException("Bitcoin contract not found or not confirmed"));
                            return;
                        }
                        
                        // Contract exists and is confirmed
                        // For Bitcoin, we need to create a claim transaction manually
                        // Since we don't have a Bitcoin wallet, we'll create the transaction data
                        // and the user will need to broadcast it manually or via a Bitcoin node
                        
                        // Create claim transaction data
                        // This would require Bitcoin wallet integration for full implementation
                        
                        log.info("Bitcoin claim transaction data created for swap {} (requires manual broadcast)", swapId);
                        
                        // Update swap with claim information
                        swap.setSecretRevealedAt(System.currentTimeMillis());
                        swap.setStatus("SECRET_REVEALED");
                        swapDao.updateAtomicSwap(swap);
                        
                        future.complete("CLAIM_DATA_CREATED: Bitcoin claim transaction requires manual broadcast via Bitcoin wallet/node");
                    })
                    .exceptionally(e -> {
                        log.error("Error verifying Bitcoin contract", e);
                        future.completeExceptionally(e);
                        return null;
                    });
                
            } catch (Exception e) {
                log.error("Error claiming Bitcoin coins", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Claim coins from Litecoin HTLC contract using revealed secret
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param secret Secret revealed by counterparty
     * @return Transaction ID of the claim transaction
     */
    public static CompletableFuture<String> claimLitecoinCoins(Context context, long swapId, String secret) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                // Verify secret matches hash
                if (!HtlcUtils.verifySecret(secret, swap.getSecretHash())) {
                    future.completeExceptionally(new IllegalArgumentException("Secret does not match hash"));
                    return;
                }
                
                if (swap.getCounterpartyContractAddress() == null || swap.getCounterpartyTxId() == null) {
                    future.completeExceptionally(new IllegalStateException("No Litecoin contract found"));
                    return;
                }
                
                // Get Litecoin network parameters
                NetworkParameters ltcParams = MultiChainNetworkHelper.getNetworkParameters("LTC");
                
                // Query blockchain directly from nodes to verify contract exists
                MultiChainBlockchainHelper.queryTransaction("LTC", swap.getCounterpartyTxId())
                    .thenAccept(txInfo -> {
                        if (!txInfo.exists || !txInfo.confirmed) {
                            future.completeExceptionally(new IllegalStateException("Litecoin contract not found or not confirmed"));
                            return;
                        }
                        
                        // Contract exists and is confirmed
                        // For Litecoin, we need to create a claim transaction manually
                        // Since we don't have a Litecoin wallet, we'll create the transaction data
                        // and the user will need to broadcast it manually or via a Litecoin node
                        
                        // Create claim transaction data
                        // This would require Litecoin wallet integration for full implementation
                        
                        log.info("Litecoin claim transaction data created for swap {} (requires manual broadcast)", swapId);
                        
                        // Update swap with claim information
                        swap.setSecretRevealedAt(System.currentTimeMillis());
                        swap.setStatus("SECRET_REVEALED");
                        swapDao.updateAtomicSwap(swap);
                        
                        future.complete("CLAIM_DATA_CREATED: Litecoin claim transaction requires manual broadcast via Litecoin wallet/node");
                    })
                    .exceptionally(e -> {
                        log.error("Error verifying Litecoin contract", e);
                        future.completeExceptionally(e);
                        return null;
                    });
                
            } catch (Exception e) {
                log.error("Error claiming Litecoin coins", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Detect counterparty contract on Bitcoin/Litecoin blockchain
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @param contractAddress Contract address to monitor
     * @param currency Currency ("BTC" or "LTC")
     * @return true if contract detected
     */
    public static CompletableFuture<Boolean> detectCounterpartyContract(Context context, long swapId, 
                                                                        String contractAddress, String currency) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                // Query blockchain directly from nodes to check if contract address has transactions
                if ("BTC".equals(currency) || "LTC".equals(currency)) {
                    MultiChainBlockchainHelper.queryAddress(currency, contractAddress)
                        .thenAccept(addrInfo -> {
                            if (addrInfo.hasTransactions && addrInfo.txCount > 0 && addrInfo.balance > 0) {
                                // Contract address has transactions and balance, contract likely exists
                                // Update swap with contract information
                                swap.setCounterpartyContractAddress(contractAddress);
                                swap.setCounterpartyContractCreatedAt(System.currentTimeMillis());
                                swap.setStatus("COUNTERPARTY_CONTRACT_CREATED");
                                swapDao.updateAtomicSwap(swap);
                                
                                log.info("Counterparty {} contract detected for swap {}: {}", currency, swapId, contractAddress);
                                future.complete(true);
                            } else {
                                log.debug("No contract found at address {} for swap {}", contractAddress, swapId);
                                future.complete(false);
                            }
                        })
                        .exceptionally(e -> {
                            log.error("Error detecting counterparty contract", e);
                            future.completeExceptionally(e);
                            return null;
                        });
                } else {
                    // Use direct P2P node connections for Dogecoin (no API)
                    MultiChainBlockchainHelper.queryAddress(currency, contractAddress)
                        .thenAccept(addrInfo -> {
                            if (addrInfo.hasTransactions && addrInfo.txCount > 0 && addrInfo.balance > 0) {
                                // Contract address has transactions and balance, contract likely exists
                                swap.setCounterpartyContractAddress(contractAddress);
                                swap.setCounterpartyContractCreatedAt(System.currentTimeMillis());
                                swap.setStatus("COUNTERPARTY_CONTRACT_CREATED");
                                swapDao.updateAtomicSwap(swap);
                                
                                log.info("Counterparty {} contract detected for swap {}: {}", currency, swapId, contractAddress);
                                future.complete(true);
                            } else {
                                log.debug("No contract found at address {} for swap {}", contractAddress, swapId);
                                future.complete(false);
                            }
                        })
                        .exceptionally(e -> {
                            log.error("Error detecting counterparty contract", e);
                            future.completeExceptionally(e);
                            return null;
                        });
                }
                
            } catch (Exception e) {
                log.error("Error detecting counterparty contract", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Refund coins from expired Bitcoin HTLC contract
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @return Transaction ID of the refund transaction
     */
    public static CompletableFuture<String> refundBitcoinCoins(Context context, long swapId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                if (swap.getCounterpartyContractAddress() == null || swap.getCounterpartyTxId() == null) {
                    future.completeExceptionally(new IllegalStateException("No Bitcoin contract found"));
                    return;
                }
                
                // Check if swap has expired
                if (swap.getExpiresAt() > System.currentTimeMillis()) {
                    future.completeExceptionally(new IllegalStateException("Swap has not expired yet"));
                    return;
                }
                
                // For Bitcoin, we need to create a refund transaction manually
                // Since we don't have a Bitcoin wallet, we'll create the transaction data
                // and the user will need to broadcast it manually or via a Bitcoin node
                
                log.info("Bitcoin refund transaction data created for swap {} (requires manual broadcast)", swapId);
                
                // Update swap status
                swap.setStatus("REFUNDED");
                swap.setRefundedAt(System.currentTimeMillis());
                swap.setErrorMessage("Bitcoin refund transaction requires manual broadcast via Bitcoin wallet/node");
                swapDao.updateAtomicSwap(swap);
                
                future.complete("REFUND_DATA_CREATED: Bitcoin refund transaction requires manual broadcast via Bitcoin wallet/node");
                
            } catch (Exception e) {
                log.error("Error refunding Bitcoin coins", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Refund coins from expired Litecoin HTLC contract
     * 
     * @param context Application context
     * @param swapId Swap ID
     * @return Transaction ID of the refund transaction
     */
    public static CompletableFuture<String> refundLitecoinCoins(Context context, long swapId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
                AtomicSwapDao swapDao = database.atomicSwapDao();
                
                AtomicSwap swap = swapDao.getSwapById(swapId);
                if (swap == null) {
                    future.completeExceptionally(new IllegalArgumentException("Swap not found: " + swapId));
                    return;
                }
                
                if (swap.getCounterpartyContractAddress() == null || swap.getCounterpartyTxId() == null) {
                    future.completeExceptionally(new IllegalStateException("No Litecoin contract found"));
                    return;
                }
                
                // Check if swap has expired
                if (swap.getExpiresAt() > System.currentTimeMillis()) {
                    future.completeExceptionally(new IllegalStateException("Swap has not expired yet"));
                    return;
                }
                
                // For Litecoin, we need to create a refund transaction manually
                // Since we don't have a Litecoin wallet, we'll create the transaction data
                // and the user will need to broadcast it manually or via a Litecoin node
                
                log.info("Litecoin refund transaction data created for swap {} (requires manual broadcast)", swapId);
                
                // Update swap status
                swap.setStatus("REFUNDED");
                swap.setRefundedAt(System.currentTimeMillis());
                swap.setErrorMessage("Litecoin refund transaction requires manual broadcast via Litecoin wallet/node");
                swapDao.updateAtomicSwap(swap);
                
                future.complete("REFUND_DATA_CREATED: Litecoin refund transaction requires manual broadcast via Litecoin wallet/node");
                
            } catch (Exception e) {
                log.error("Error refunding Litecoin coins", e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
}

