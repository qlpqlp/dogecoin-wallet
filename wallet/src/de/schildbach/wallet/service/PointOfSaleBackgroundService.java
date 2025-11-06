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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.Product;
import de.schildbach.wallet.data.ProductDao;
import de.schildbach.wallet.ui.ProductManagementActivity;
import de.schildbach.wallet.service.BlockchainService;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Background service for Point of Sale Mode
 * 
 * Runs the web server and monitors mempool for payments even when the phone is off
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class PointOfSaleBackgroundService extends Service {
    private static final Logger log = LoggerFactory.getLogger(PointOfSaleBackgroundService.class);
    private static final String CHANNEL_ID = "pos_background_service";
    private static final int NOTIFICATION_ID = 1001;
    
    private WalletApplication application;
    private PointOfSaleWebService webService;
    private WakeLock wakeLock;
    private HandlerThread serviceThread;
    private Handler serviceHandler;
    private Wallet wallet;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, PointOfSaleBackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    public static void stop(Context context) {
        Intent intent = new Intent(context, PointOfSaleBackgroundService.class);
        context.stopService(intent);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        log.info("PointOfSaleBackgroundService onCreate");
        
        application = (WalletApplication) getApplication();
        
        // Create wake lock to keep device awake
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PointOfSaleBackgroundService::WakeLock");
        wakeLock.acquire();
        
        // Create service thread
        serviceThread = new HandlerThread("PointOfSaleBackgroundService");
        serviceThread.start();
        serviceHandler = new Handler(serviceThread.getLooper());
        
        // Create notification channel
        createNotificationChannel();
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Initialize web service
        webService = new PointOfSaleWebService(application);
        webService.start();
        
        // Ensure blockchain service is running for mempool monitoring
        BlockchainService.ensureRunning(application);
        
        // Get wallet and listen for payments
        application.getWalletAsync(w -> {
            this.wallet = w;
            if (w != null) {
                // Listen for payments
                w.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
                    @Override
                    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                        handlePayment(tx);
                    }
                });
                
                log.info("PointOfSaleBackgroundService: Wallet listener attached");
            }
        });
        
        log.info("PointOfSaleBackgroundService started");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.info("PointOfSaleBackgroundService onStartCommand");
        // Return START_STICKY to keep service running even if killed
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        log.info("PointOfSaleBackgroundService onDestroy");
        
        // Stop web service
        if (webService != null) {
            webService.stop();
        }
        
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
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
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Point of Sale Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Runs Point of Sale web server and monitors payments in background");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, ProductManagementActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Point of Sale Active")
            .setContentText("Web server running and monitoring payments")
            .setSmallIcon(R.drawable.ic_store_white_24dp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }
    
    private void handlePayment(Transaction tx) {
        AddressBookDatabase database = AddressBookDatabase.getDatabase(application);
        ProductDao productDao = database.productDao();
        
        for (TransactionOutput output : tx.getOutputs()) {
            try {
                Address address = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                
                // Check database for products with this payment address
                Product product = productDao.getProductByPaymentAddress(address.toString());
                
                if (product != null && product.getPaymentAddress() != null) {
                    Coin receivedAmount = output.getValue();
                    long expectedPrice = product.getPriceDoge() * product.getRequestedQuantity();
                    Coin expectedAmount = Coin.valueOf(expectedPrice);
                    
                    // Check if payment amount matches (with small tolerance)
                    if (receivedAmount.compareTo(expectedAmount) >= 0) {
                        // Deduct quantity (only if not unlimited)
                        if (product.getQuantity() != -1) {
                            int newQuantity = product.getQuantity() - product.getRequestedQuantity();
                            if (newQuantity < 0) newQuantity = 0;
                            product.setQuantity(newQuantity);
                        }
                        // Unlimited products stay unlimited (-1)
                        
                        product.setPaymentAddress(null); // Clear payment address
                        product.setRequestedQuantity(0);
                        productDao.updateProduct(product);
                        
                        log.info("Payment received for product {}: {} DOGE", product.getName(), receivedAmount.toFriendlyString());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing payment", e);
            }
        }
    }
}

