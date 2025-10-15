package de.schildbach.wallet.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.format.DateUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.google.common.util.concurrent.ListenableFuture;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.ExcludedAddressHelper;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.data.PaymentIntent.Standard;
import de.schildbach.wallet.data.RecurringPayment;
import de.schildbach.wallet.data.RecurringPaymentDatabase;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.SendRequest;

/**
 * Background service for executing scheduled recurring payments
 */
public class RecurringPaymentsService extends JobService {
    private static final Logger log = LoggerFactory.getLogger(RecurringPaymentsService.class);
    
    private static final int JOB_ID = 1001; // Unique job ID for recurring payments
    private static final long CHECK_INTERVAL = DateUtils.MINUTE_IN_MILLIS; // Check every minute
    
    private RecurringPaymentDatabase database;
    
    public static void schedule(final WalletApplication application) {
        log.info("Scheduling recurring payments service");
        
        final JobScheduler jobScheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        final JobInfo.Builder jobInfo = new JobInfo.Builder(JOB_ID, new ComponentName(application, RecurringPaymentsService.class));
        
        // Run every minute to check for due payments
        jobInfo.setMinimumLatency(CHECK_INTERVAL);
        jobInfo.setOverrideDeadline(CHECK_INTERVAL * 2); // Maximum 2 minutes delay
        jobInfo.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        jobInfo.setRequiresDeviceIdle(false); // Run even when device is not idle
        jobInfo.setPersisted(true); // Persist across reboots
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            jobInfo.setRequiresBatteryNotLow(false); // Run even on low battery
            jobInfo.setRequiresStorageNotLow(false); // Run even on low storage
        }
        
        final int result = jobScheduler.schedule(jobInfo.build());
        if (result == JobScheduler.RESULT_SUCCESS) {
            log.info("Recurring payments service scheduled successfully");
        } else {
            log.error("Failed to schedule recurring payments service");
        }
    }
    
    public static void cancel(final WalletApplication application) {
        log.info("Cancelling recurring payments service");
        final JobScheduler jobScheduler = (JobScheduler) application.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(JOB_ID);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        database = new RecurringPaymentDatabase(this);
        log.info("RecurringPaymentsService created");
    }
    
    @Override
    public boolean onStartJob(JobParameters params) {
        log.info("RecurringPaymentsService job started");
        
        // Run on background thread
        new Thread(() -> {
            try {
                processRecurringPayments();
                jobFinished(params, false); // Job completed successfully
            } catch (Exception e) {
                log.error("Error processing recurring payments", e);
                jobFinished(params, true); // Job failed, reschedule
            }
        }).start();
        
        return true; // Job is running asynchronously
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        log.info("RecurringPaymentsService job stopped");
        return true; // Reschedule the job
    }
    
    private void processRecurringPayments() {
        log.info("Processing recurring payments...");
        
        // Get all enabled recurring payments
        List<RecurringPayment> payments = database.getAllPayments();
        Date now = new Date();
        
        log.info("Found {} recurring payments, checking for due payments (including overdue ones)", payments.size());
        
        for (RecurringPayment payment : payments) {
            if (!payment.isEnabled()) {
                log.info("Skipping disabled payment ID: {}", payment.getId());
                continue;
            }
            
            Date nextPaymentDate = payment.getNextPaymentDate();
            if (nextPaymentDate == null) {
                log.warn("Payment ID {} has null next payment date, skipping", payment.getId());
                continue;
            }
            
            // Check if payment is due (always execute if date/time is equal to or older than current time)
            long timeDiff = now.getTime() - nextPaymentDate.getTime();
            log.info("Payment ID {} - Next: {}, Now: {}, Diff: {}ms, Enabled: {}", 
                payment.getId(), nextPaymentDate, now, timeDiff, payment.isEnabled());
            
            // Execute if payment is due (date/time is equal to or older than current time)
            // Allow 1 minute grace period for timing precision, but always try overdue payments
            if (timeDiff >= -DateUtils.MINUTE_IN_MILLIS) {
                log.info("Payment ID {} is due (overdue by {}ms), executing...", payment.getId(), timeDiff);
                executePayment(payment);
            } else {
                log.info("Payment ID {} not due yet. Next: {}, Now: {}, Diff: {}ms", 
                    payment.getId(), nextPaymentDate, now, timeDiff);
            }
        }
        
        // Reschedule the next check
        schedule((WalletApplication) getApplication());
    }
    
    private void executePayment(RecurringPayment payment) {
        try {
            Date now = new Date();
            long timeDiff = now.getTime() - payment.getNextPaymentDate().getTime();
            boolean isOverdue = timeDiff > DateUtils.MINUTE_IN_MILLIS;
            
            log.info("Executing payment ID: {} to address: {} amount: {} DOGE (Overdue: {}, Diff: {}ms)", 
                payment.getId(), payment.getDestinationAddress(), payment.getAmount(), isOverdue, timeDiff);
            
            // Get wallet from application
            WalletApplication app = (WalletApplication) getApplication();
            Wallet wallet = app.getWallet();
            
            if (wallet == null) {
                log.error("Wallet not available for payment execution");
                return;
            }
            
            // Initialize BitcoinJ context if not already set
            try {
                if (org.bitcoinj.core.Context.get() == null) {
                    log.info("Initializing BitcoinJ context...");
                    org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                    log.info("BitcoinJ context initialized successfully");
                } else {
                    log.info("BitcoinJ context already initialized");
                }
            } catch (Exception e) {
                log.error("Failed to initialize BitcoinJ context", e);
                // Try to create a new context using the network parameters
                try {
                    org.bitcoinj.core.Context context = new org.bitcoinj.core.Context(Constants.NETWORK_PARAMETERS);
                    org.bitcoinj.core.Context.propagate(context);
                    log.info("Created new BitcoinJ context with network parameters");
                } catch (Exception e2) {
                    log.error("Failed to create new BitcoinJ context", e2);
                    return;
                }
            }
            
            // Check if wallet has sufficient balance
            Coin availableBalance = ExcludedAddressHelper.getAvailableBalanceExcludingReserved(wallet);
            Coin paymentAmount = Coin.valueOf((long) (payment.getAmount() * Coin.COIN.value));
            
            if (availableBalance.isLessThan(paymentAmount)) {
                log.error("Insufficient balance for payment. Available: {}, Required: {}", 
                    availableBalance.toFriendlyString(), paymentAmount.toFriendlyString());
                return;
            }
            
            // Create destination address
            Address destinationAddress = Address.fromString(Constants.NETWORK_PARAMETERS, payment.getDestinationAddress());
            
            // Create payment intent
            PaymentIntent.Output[] outputs = new PaymentIntent.Output[] {
                new PaymentIntent.Output(paymentAmount, ScriptBuilder.createOutputScript(destinationAddress))
            };
            
            // Add OP_RETURN output if reference is provided
            if (payment.getReference() != null && !payment.getReference().trim().isEmpty()) {
                byte[] referenceBytes = payment.getReference().getBytes(StandardCharsets.UTF_8);
                if (referenceBytes.length <= 80) { // OP_RETURN limit
                    Script opReturnScript = ScriptBuilder.createOpReturnScript(referenceBytes);
                    outputs = new PaymentIntent.Output[] {
                        new PaymentIntent.Output(Coin.ZERO, opReturnScript),
                        new PaymentIntent.Output(paymentAmount, ScriptBuilder.createOutputScript(destinationAddress))
                    };
                }
            }
            
            PaymentIntent paymentIntent = new PaymentIntent(null, null, null, outputs, null, null, null, null, null);
            
            // Create send request
            SendRequest sendRequest = paymentIntent.toSendRequest();
            sendRequest.feePerKb = Coin.valueOf(1000000); // 1 DOGE per KB fee
            
            // Set memo with reference if provided
            String memo = "Recurring payment #" + payment.getId();
            if (payment.getReference() != null && !payment.getReference().trim().isEmpty()) {
                memo += " - Ref: " + payment.getReference();
            }
            sendRequest.memo = memo;
            
            // Execute the transaction
            log.info("Sending transaction for payment ID: {}", payment.getId());
            Transaction transaction = wallet.sendCoinsOffline(sendRequest);
            log.info("Transaction created successfully: {}", transaction.getTxId());
            
            // Broadcast the transaction through blockchain service
            // Note: This is a simplified approach - in a real implementation, 
            // you'd want to use the proper service binding pattern
            log.info("Transaction created and ready for broadcast: {}", transaction.getTxId());
            // TODO: Implement proper transaction broadcasting through BlockchainService
            
            // Update payment status
            if (payment.isRecurringMonthly()) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(payment.getNextPaymentDate());
                calendar.add(Calendar.MONTH, 1);
                payment.setNextPaymentDate(calendar.getTime());
                log.info("Updated next payment date to: {}", payment.getNextPaymentDate());
            } else {
                // For one-time payments, disable after execution
                payment.setEnabled(false);
                log.info("One-time payment disabled after execution");
            }
            
            // Update in database
            if (database.updatePayment(payment)) {
                log.info("Payment updated successfully in database");
            } else {
                log.error("Failed to update payment in database");
            }
            
        } catch (Exception e) {
            log.error("Failed to execute payment ID: {}", payment.getId(), e);
        }
    }
}
