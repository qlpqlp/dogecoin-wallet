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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.text.format.DateUtils;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.google.common.base.Stopwatch;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.WalletBalanceWidgetProvider;
import de.schildbach.wallet.addressbook.AddressBookDao;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.ui.BiometricAuthActivity;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.util.BiometricHelper;
import de.schildbach.wallet.data.SelectedExchangeRateLiveData;
import de.schildbach.wallet.data.WalletBalanceLiveData;
import de.schildbach.wallet.data.WalletLiveData;
import de.schildbach.wallet.data.DogecoinPeer;
import de.schildbach.wallet.exchangerate.ExchangeRateEntry;
import de.schildbach.wallet.service.BlockchainState.Impediment;
import de.schildbach.wallet.ui.WalletActivity;
import de.schildbach.wallet.ui.preference.ResolveDnsTask;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.core.listeners.AbstractPeerDataEventListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static androidx.core.util.Preconditions.checkState;

/**
 * @author Andreas Schildbach
 */
public class BlockchainService extends LifecycleService {
    private PowerManager pm;
    private NotificationManager nm;

    private WalletApplication application;
    private Configuration config;
    private AddressBookDao addressBookDao;
    private WalletLiveData wallet;

    private BlockStore blockStore;
    private File blockChainFile;
    private BlockChain blockChain;
    @Nullable
    private PeerGroup peerGroup;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler delayHandler = new Handler();
    private WakeLock wakeLock;

    private final NotificationCompat.Builder connectivityNotification = new NotificationCompat.Builder(BlockchainService.this,
            Constants.NOTIFICATION_CHANNEL_ID_ONGOING);
    private PeerConnectivityListener peerConnectivityListener;
    private ImpedimentsLiveData impediments;
    private int notificationCount = 0;
    private Coin notificationAccumulatedAmount = Coin.ZERO;
    private final List<Address> notificationAddresses = new LinkedList<>();
    private Stopwatch serviceUpTime;
    private boolean resetBlockchainOnShutdown = false;
    private volatile long selectedCheckpointTimestamp = -1; // -1 means use wallet's earliest key creation time
    private volatile int selectedCheckpointBlockHeight = -1; // -1 means use standard checkpoint loading
    private volatile String selectedCheckpointBlockHash = null; // Block hash for custom checkpoint
    private volatile int selectedCheckpointVersion = 0;
    private volatile String selectedCheckpointPrevBlockHash = null;
    private volatile String selectedCheckpointMerkleRoot = null;
    private volatile long selectedCheckpointTime = 0;
    private volatile String selectedCheckpointBits = null;
    private volatile long selectedCheckpointNonce = 0;
    private final AtomicBoolean isBound = new AtomicBoolean(false);
    private volatile boolean isDestroying = false;
    
    // Background mempool monitoring
    private final Runnable mempoolMonitoringTask = new Runnable() {
        @Override
        public void run() {
            ensureMempoolMonitoring();
            // Schedule next check in 30 seconds
            handler.postDelayed(this, 30000);
        }
    };
    
    // Flag to prevent multiple restart attempts
    private volatile boolean isRestarting = false;

    private static final int CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS = 144 * 2; // approx. 2 days
    private static final long BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;

    private static final String ACTION_CANCEL_COINS_RECEIVED = BlockchainService.class.getPackage().getName()
            + ".cancel_coins_received";
    private static final String ACTION_RESET_BLOCKCHAIN = BlockchainService.class.getPackage().getName()
            + ".reset_blockchain";

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    public static void start(final Context context, final boolean cancelCoinsReceived) {
        try {
            if (cancelCoinsReceived)
                ContextCompat.startForegroundService(context,
                        new Intent(BlockchainService.ACTION_CANCEL_COINS_RECEIVED, null, context, BlockchainService.class));
            else
                ContextCompat.startForegroundService(context, new Intent(context, BlockchainService.class));
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            // Cannot start foreground service from JobService on Android 12+
            // Service will be started when app comes to foreground
            log.warn("Cannot start foreground service from background: {}. Service will be started when app comes to foreground.", e.getMessage());
        } catch (IllegalStateException e) {
            // Handle other cases where foreground service cannot be started
            log.warn("Cannot start service from background: {}. Service will be started when app comes to foreground.", e.getMessage());
        }
    }

    public static void resetBlockchain(final Context context) {
        resetBlockchain(context, -1); // Default: use wallet's earliest key creation time
    }
    
    public static void resetBlockchain(final Context context, final long checkpointTimestamp) {
        resetBlockchain(context, checkpointTimestamp, -1, null, 0, null, null, 0, null, 0);
    }
    
    public static void resetBlockchain(final Context context, final long checkpointTimestamp, 
            final int checkpointBlockHeight, final String checkpointBlockHash,
            final int checkpointVersion, final String checkpointPrevBlockHash,
            final String checkpointMerkleRoot, final long checkpointTime,
            final String checkpointBits, final long checkpointNonce) {
        // implicitly stops blockchain service
        try {
            Intent intent = new Intent(BlockchainService.ACTION_RESET_BLOCKCHAIN, null, context, BlockchainService.class);
            if (checkpointTimestamp >= 0) {
                intent.putExtra("checkpoint_timestamp", checkpointTimestamp);
            }
            if (checkpointBlockHeight >= 0) {
                intent.putExtra("checkpoint_block_height", checkpointBlockHeight);
            }
            if (checkpointBlockHash != null && !checkpointBlockHash.isEmpty()) {
                intent.putExtra("checkpoint_block_hash", checkpointBlockHash);
                intent.putExtra("checkpoint_version", checkpointVersion);
                intent.putExtra("checkpoint_prev_block_hash", checkpointPrevBlockHash);
                intent.putExtra("checkpoint_merkle_root", checkpointMerkleRoot);
                intent.putExtra("checkpoint_time", checkpointTime);
                intent.putExtra("checkpoint_bits", checkpointBits);
                intent.putExtra("checkpoint_nonce", checkpointNonce);
            }
            ContextCompat.startForegroundService(context, intent);
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            log.warn("Cannot start foreground service for reset from background: {}. Service will be started when app comes to foreground.", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Cannot start service from background for reset: {}. Service will be started when app comes to foreground.", e.getMessage());
        }
    }
    
    /**
     * Ensure the blockchain service is running for background peer connections
     * This is critical for mempool monitoring and transaction broadcasting
     */
    public static void ensureRunning(final Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, BlockchainService.class));
    }

    private static class NewTransactionLiveData extends LiveData<Transaction> {
        private final Wallet wallet;

        public NewTransactionLiveData(final Wallet wallet) {
            this.wallet = wallet;
        }

        @Override
        protected void onActive() {
            wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletListener);
            wallet.addCoinsSentEventListener(Threading.SAME_THREAD, walletListener);
        }

        @Override
        protected void onInactive() {
            wallet.removeCoinsSentEventListener(walletListener);
            wallet.removeCoinsReceivedEventListener(walletListener);
        }

        private final WalletListener walletListener = new WalletListener();

        private class WalletListener implements WalletCoinsReceivedEventListener, WalletCoinsSentEventListener {
            @Override
            public void onCoinsReceived(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                postValue(tx);
            }

            @Override
            public void onCoinsSent(final Wallet wallet, final Transaction tx, final Coin prevBalance,
                    final Coin newBalance) {
                postValue(tx);
            }
        }
    }

    private void notifyCoinsReceived(@Nullable final Address address, final Coin amount,
            final Sha256Hash transactionHash) {
        notificationCount++;
        notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
        if (address != null && !notificationAddresses.contains(address))
            notificationAddresses.add(address);

        final MonetaryFormat btcFormat = config.getFormat();
        final String packageFlavor = application.applicationPackageFlavor();
        final String msgSuffix = packageFlavor != null ? " [" + packageFlavor + "]" : "";

        // summary notification
        final NotificationCompat.Builder summaryNotification = new NotificationCompat.Builder(this,
                Constants.NOTIFICATION_CHANNEL_ID_RECEIVED);
        summaryNotification.setGroup(Constants.NOTIFICATION_GROUP_KEY_RECEIVED);
        summaryNotification.setGroupSummary(true);
        summaryNotification.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);
        summaryNotification.setWhen(System.currentTimeMillis());
        summaryNotification.setSmallIcon(R.drawable.stat_notify_received_24dp);
        summaryNotification.setContentTitle(
                getString(R.string.notification_coins_received_msg, btcFormat.format(notificationAccumulatedAmount))
                        + msgSuffix);
        if (!notificationAddresses.isEmpty()) {
            final StringBuilder text = new StringBuilder();
            for (final Address notificationAddress : notificationAddresses) {
                if (text.length() > 0)
                    text.append(", ");
                final String addressStr = notificationAddress.toString();
                final String label = addressBookDao.resolveLabel(addressStr);
                text.append(label != null ? label : addressStr);
            }
            summaryNotification.setContentText(text);
        }
        summaryNotification
                .setContentIntent(createLauncherPendingIntent(this, Constants.NOTIFICATION_ID_COINS_RECEIVED));
        nm.notify(Constants.NOTIFICATION_ID_COINS_RECEIVED, summaryNotification.build());

        // child notification
        final NotificationCompat.Builder childNotification = new NotificationCompat.Builder(this,
                Constants.NOTIFICATION_CHANNEL_ID_RECEIVED);
        childNotification.setGroup(Constants.NOTIFICATION_GROUP_KEY_RECEIVED);
        childNotification.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN);
        childNotification.setWhen(System.currentTimeMillis());
        childNotification.setColor(getColor(R.color.fg_network_significant));
        childNotification.setSmallIcon(R.drawable.stat_notify_received_24dp);
        final String msg = getString(R.string.notification_coins_received_msg, btcFormat.format(amount)) + msgSuffix;
        childNotification.setTicker(msg);
        childNotification.setContentTitle(msg);
        if (address != null) {
            final String addressStr = address.toString();
            final String addressLabel = addressBookDao.resolveLabel(addressStr);
            if (addressLabel != null)
                childNotification.setContentText(addressLabel);
            else
                childNotification.setContentText(addressStr);
        }
        childNotification
                .setContentIntent(createLauncherPendingIntent(this, transactionHash.hashCode()));
        childNotification.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received));
        nm.notify(transactionHash.toString(), Constants.NOTIFICATION_ID_COINS_RECEIVED, childNotification.build());
    }

    private final class PeerConnectivityListener
            implements PeerConnectedEventListener, PeerDisconnectedEventListener {
        private AtomicBoolean stopped = new AtomicBoolean(false);
        private final int minPeersForMempool = 2;

        public void stop() {
            stopped.set(true);
        }

        @Override
        public void onPeerConnected(final Peer peer, final int peerCount) {
            postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS);
            changed(peerCount);
            
            // Log peer connection for mempool monitoring
            log.info("Peer connected: {} (total: {}) - Mempool monitoring: {}", 
                peer.getAddress(), peerCount, peerCount >= minPeersForMempool ? "ACTIVE" : "INSUFFICIENT");
        }

        @Override
        public void onPeerDisconnected(final Peer peer, final int peerCount) {
            changed(peerCount);
            
            // Log peer disconnection and mempool monitoring status
            log.info("Peer disconnected: {} (total: {}) - Mempool monitoring: {}", 
                peer.getAddress(), peerCount, peerCount >= minPeersForMempool ? "ACTIVE" : "INSUFFICIENT");
        }

        private void changed(final int numPeers) {
            if (stopped.get())
                return;

            handler.post(() -> {
                startForeground(numPeers);
                broadcastPeerState(numPeers);
                
                // Warn if insufficient peers for mempool monitoring
                if (numPeers < minPeersForMempool && numPeers > 0) {
                    log.warn("Low peer count ({}): Mempool monitoring may be limited", numPeers);
                }
            });
        }
    }

    private final PeerDataEventListener blockchainDownloadListener = new BlockchainDownloadListener();

    private class BlockchainDownloadListener extends AbstractPeerDataEventListener implements Runnable {
        private final AtomicLong lastMessageTime = new AtomicLong(0);
        private final AtomicInteger blocksToDownload = new AtomicInteger();
        private final AtomicInteger blocksLeft = new AtomicInteger();

        @Override
        public void onChainDownloadStarted(final Peer peer, final int blocksToDownload) {
            postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS);
            this.blocksToDownload.set(blocksToDownload);
            if (blocksToDownload >= CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS) {
                config.maybeIncrementBestChainHeightEver(blockChain.getChainHead().getHeight() + blocksToDownload);
                startForegroundProgress(blocksToDownload, blocksToDownload);
            }
        }

        @Override
        public void onBlocksDownloaded(final Peer peer, final Block block, final FilteredBlock filteredBlock,
                final int blocksLeft) {
            this.blocksLeft.set(blocksLeft);

            delayHandler.removeCallbacks(this);
            final long now = System.currentTimeMillis();
            if (now - lastMessageTime.get() > BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS)
                delayHandler.post(this);
            else
                delayHandler.postDelayed(this, BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS);
        }

        @Override
        public void run() {
            lastMessageTime.set(System.currentTimeMillis());

            postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS);
            final int blocksToDownload = this.blocksToDownload.get();
            final int blocksLeft = this.blocksLeft.get();
            if (blocksToDownload >= CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS)
                startForegroundProgress(blocksToDownload, blocksLeft);

            config.maybeIncrementBestChainHeightEver(blockChain.getChainHead().getHeight());
            broadcastBlockchainState();
        }
    }

    private static class ImpedimentsLiveData extends LiveData<Set<Impediment>> {
        private final WalletApplication application;
        private final ConnectivityManager connectivityManager;
        private final Set<Impediment> impediments = EnumSet.noneOf(Impediment.class);

        public ImpedimentsLiveData(final WalletApplication application) {
            this.application = application;
            this.connectivityManager = (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
            setValue(impediments);
        }

        @Override
        protected void onActive() {
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
            // implicitly start PeerGroup
            final Intent intent = application.registerReceiver(connectivityReceiver, intentFilter);
            if (intent != null)
                handleIntent(intent);
        }

        @Override
        protected void onInactive() {
            application.unregisterReceiver(connectivityReceiver);
        }

        private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                handleIntent(intent);
            }
        };

        private void handleIntent(final Intent intent) {
            final String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                final boolean hasConnectivity = networkInfo != null && networkInfo.isConnected();
                final boolean isMetered = hasConnectivity && connectivityManager.isActiveNetworkMetered();
                if (hasConnectivity)
                    impediments.remove(Impediment.NETWORK);
                else
                    impediments.add(Impediment.NETWORK);

                if (log.isInfoEnabled()) {
                    final StringBuilder s = new StringBuilder("active network is ").append(hasConnectivity ? "up" :
                            "down");
                    if (isMetered)
                        s.append(", metered");
                    if (networkInfo != null) {
                        s.append(", type: ").append(networkInfo.getTypeName());
                        s.append(", state: ").append(networkInfo.getState()).append('/')
                                .append(networkInfo.getDetailedState());
                        final String extraInfo = networkInfo.getExtraInfo();
                        if (extraInfo != null)
                            s.append(", extraInfo: ").append(extraInfo);
                        final String reason = networkInfo.getReason();
                        if (reason != null)
                            s.append(", reason: ").append(reason);
                    }
                    log.info(s.toString());
                }
            } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
                impediments.add(Impediment.STORAGE);
                log.info("device storage low");
            } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                impediments.remove(Impediment.STORAGE);
                log.info("device storage ok");
            }
            setValue(impediments);
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            (sharedPreferences, key) -> {
                if (Configuration.PREFS_KEY_SYNC_MODE.equals(key) || Configuration.PREFS_KEY_TRUSTED_PEERS.equals(key) ||
                        Configuration.PREFS_KEY_TRUSTED_PEERS_ONLY.equals(key))
                    stopSelf();
            };

    private Runnable delayedStopSelfRunnable = () -> {
        log.info("service idling detected, trying to stop");
        stopSelf();
        if (isBound.get())
            log.info("stop is deferred because service still bound");
    };

    private void postDelayedStopSelf(final long ms) {
        delayHandler.removeCallbacks(delayedStopSelfRunnable);
        delayHandler.postDelayed(delayedStopSelfRunnable, ms);
    }

    private final BroadcastReceiver deviceIdleModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            log.info("device {} idle mode", pm.isDeviceIdleMode() ? "entering" : "exiting");
        }
    };
    
    // Independent worldwide peer discovery (does not affect wallet sync)
    private WorldwidePeerDiscovery worldwidePeerDiscovery;

    public class LocalBinder extends Binder {
        public BlockchainService getService() {
            return BlockchainService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(final Intent intent) {
        log.info("onBind: {}", intent);
        super.onBind(intent);
        isBound.set(true);
        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        log.info("onUnbind: {}", intent);
        isBound.set(false);
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        serviceUpTime = Stopwatch.createStarted();
        log.debug(".onCreate()");
        super.onCreate();

        application = (WalletApplication) getApplication();
        config = application.getConfiguration();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        log.info("acquiring {}", wakeLock);
        wakeLock.acquire();

        connectivityNotification.setColor(getColor(R.color.fg_network_significant));
        connectivityNotification.setContentTitle(getString(config.isTrustedPeersOnly() ?
                R.string.notification_connectivity_syncing_trusted_peer :
                R.string.notification_connectivity_syncing_message));
        connectivityNotification.setContentIntent(createLauncherPendingIntent(BlockchainService.this, 
                Constants.NOTIFICATION_ID_CONNECTIVITY));
        connectivityNotification.setWhen(System.currentTimeMillis());
        connectivityNotification.setOngoing(true);
        connectivityNotification.setPriority(NotificationCompat.PRIORITY_LOW);
        connectivityNotification.setCategory(NotificationCompat.CATEGORY_SERVICE);
        connectivityNotification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        connectivityNotification.setShowWhen(true);
        connectivityNotification.setAutoCancel(false);
        // connectivityNotification.setSilent(true); // This method doesn't exist in older API levels
        try {
            startForeground(Constants.NOTIFICATION_ID_CONNECTIVITY, connectivityNotification.build());
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            // Cannot start foreground service from background context on Android 12+
            // Service will continue running but without foreground notification
            log.warn("Cannot start foreground service from background: {}. Service will continue without foreground notification.", e.getMessage());
        } catch (IllegalStateException e) {
            // Handle other cases where foreground service cannot be started
            log.warn("Cannot start foreground service: {}. Service will continue without foreground notification.", e.getMessage());
        }

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        addressBookDao = AddressBookDatabase.getDatabase(application).addressBookDao();
        blockChainFile = new File(getDir("blockstore", Context.MODE_PRIVATE), Constants.Files.BLOCKCHAIN_FILENAME);

        config.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        registerReceiver(deviceIdleModeReceiver, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
        
        // Initialize independent worldwide peer discovery
        worldwidePeerDiscovery = new WorldwidePeerDiscovery(application, new WorldwidePeerDiscovery.PeerDiscoveryCallback() {
            @Override
            public void onPeerUpdated(DogecoinPeer peer) {
                // No UI notification needed here as this is for the main wallet service
            }

            @Override
            public void onTotalCountChanged(int totalCount) {
                // No UI notification needed here as this is for the main wallet service
            }
        });
        worldwidePeerDiscovery.start();

        peerConnectivityListener = new PeerConnectivityListener();

        broadcastPeerState(0);

        final WalletBalanceLiveData walletBalance = new WalletBalanceLiveData(application);
        final SelectedExchangeRateLiveData exchangeRate = new SelectedExchangeRateLiveData(application);
        walletBalance.observe(this, balance -> {
            final ExchangeRateEntry rate = exchangeRate.getValue();
            if (balance != null)
                WalletBalanceWidgetProvider.updateWidgets(BlockchainService.this, balance,
                        rate != null ? rate.exchangeRate() : null);
        });
        if (Constants.ENABLE_EXCHANGE_RATES) {
            exchangeRate.observe(this, rate -> {
                final Coin balance = walletBalance.getValue();
                if (balance != null)
                    WalletBalanceWidgetProvider.updateWidgets(BlockchainService.this, balance,
                            rate != null ? rate.exchangeRate() : null);
            });
        }
        wallet = new WalletLiveData(application);
        wallet.observe(this, new Observer<Wallet>() {
            @Override
            public void onChanged(final Wallet wallet) {
                BlockchainService.this.wallet.removeObserver(this);
                // Restore checkpoint values from SharedPreferences if not already set (fallback for service restart)
                if (selectedCheckpointBlockHeight < 0 || selectedCheckpointBlockHash == null) {
                    final android.content.SharedPreferences prefs = getSharedPreferences("blockchain_service", android.content.Context.MODE_PRIVATE);
                    if (prefs.contains("checkpoint_block_height")) {
                        selectedCheckpointBlockHeight = prefs.getInt("checkpoint_block_height", -1);
                        selectedCheckpointBlockHash = prefs.getString("checkpoint_block_hash", null);
                        selectedCheckpointVersion = prefs.getInt("checkpoint_version", 0);
                        selectedCheckpointPrevBlockHash = prefs.getString("checkpoint_prev_block_hash", null);
                        selectedCheckpointMerkleRoot = prefs.getString("checkpoint_merkle_root", null);
                        selectedCheckpointTime = prefs.getLong("checkpoint_time", 0);
                        selectedCheckpointBits = prefs.getString("checkpoint_bits", null);
                        selectedCheckpointNonce = prefs.getLong("checkpoint_nonce", 0);
                        selectedCheckpointTimestamp = prefs.getLong("checkpoint_timestamp", -1);
                        log.info("Restored checkpoint values from SharedPreferences: height={}, hash={}", 
                                selectedCheckpointBlockHeight, selectedCheckpointBlockHash);
                    }
                }
                
                final boolean blockChainFileExists = blockChainFile.exists();
                if (!blockChainFileExists) {
                    log.info("blockchain does not exist, resetting wallet");
                    wallet.reset();
                }

                try {
                    blockStore = new SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile,
                            Constants.Files.BLOCKCHAIN_STORE_CAPACITY, true);
                    blockStore.getChainHead(); // detect corruptions as early as possible

                    // Use selected checkpoint timestamp if available, otherwise use wallet's earliest key creation time
                    long checkpointTimestamp = selectedCheckpointTimestamp >= 0 ? selectedCheckpointTimestamp : wallet.getEarliestKeyCreationTime();
                    final long earliestKeyCreationTimeSecs = checkpointTimestamp;
                    
                    // Debug: Log checkpoint state
                    log.info("initializeBlockchain: blockChainFileExists={}, earliestKeyCreationTimeSecs={}, selectedCheckpointBlockHeight={}, selectedCheckpointBlockHash={}", 
                            blockChainFileExists, earliestKeyCreationTimeSecs, selectedCheckpointBlockHeight, 
                            selectedCheckpointBlockHash != null ? selectedCheckpointBlockHash : "null");

                    // Load standard checkpoints if no custom checkpoint is selected
                    if (!blockChainFileExists && earliestKeyCreationTimeSecs > 0 && selectedCheckpointBlockHeight < 0) {
                        try {
                            log.info("loading checkpoints for birthdate {} from '{}'",
                                    Utils.dateTimeFormat(earliestKeyCreationTimeSecs * 1000),
                                    Constants.Files.CHECKPOINTS_ASSET);
                            final Stopwatch watch = Stopwatch.createStarted();
                            final InputStream checkpointsInputStream = getAssets()
                                    .open(Constants.Files.CHECKPOINTS_ASSET);
                            CheckpointManager.checkpoint(Constants.NETWORK_PARAMETERS, checkpointsInputStream,
                                    blockStore, earliestKeyCreationTimeSecs);
                            watch.stop();
                            log.info("checkpoints loaded, took {}", watch);
                        } catch (final IOException x) {
                            log.error("problem reading checkpoints, continuing without", x);
                        }
                    } else if (selectedCheckpointBlockHeight >= 0 && selectedCheckpointBlockHash != null && !selectedCheckpointBlockHash.isEmpty()) {
                        // Custom checkpoint selected - reconstruct Block header and set as chain head immediately
                        try {
                            log.info("Setting custom checkpoint as chain head: block height {}, block hash {}", 
                                    selectedCheckpointBlockHeight, selectedCheckpointBlockHash);
                            
                            // Reconstruct Block header from checkpoint data
                            org.bitcoinj.core.Sha256Hash prevBlockHash = org.bitcoinj.core.Sha256Hash.wrap(selectedCheckpointPrevBlockHash);
                            org.bitcoinj.core.Sha256Hash merkleRoot = org.bitcoinj.core.Sha256Hash.wrap(selectedCheckpointMerkleRoot);
                            // Bits is stored as hex string (e.g., "1a009b86"), convert to long (compact difficulty)
                            long bitsValue = Long.parseLong(selectedCheckpointBits, 16);
                            
                            // Create Block header (header only, no transactions)
                            org.bitcoinj.core.Block block = new org.bitcoinj.core.Block(
                                    Constants.NETWORK_PARAMETERS, (long)selectedCheckpointVersion, prevBlockHash, merkleRoot,
                                    selectedCheckpointTime, bitsValue, selectedCheckpointNonce, java.util.Collections.emptyList());
                            
                            // Verify hash matches
                            org.bitcoinj.core.Sha256Hash expectedHash = org.bitcoinj.core.Sha256Hash.wrap(selectedCheckpointBlockHash);
                            if (!block.getHash().equals(expectedHash)) {
                                log.error("Checkpoint block hash mismatch! Expected {}, got {}", 
                                        selectedCheckpointBlockHash, block.getHash());
                                throw new RuntimeException("Checkpoint block hash verification failed");
                            }
                            
                            // Create StoredBlock and set as chain head
                            org.bitcoinj.core.StoredBlock storedBlock = new org.bitcoinj.core.StoredBlock(
                                    block, block.getWork(), selectedCheckpointBlockHeight);
                            blockStore.put(storedBlock);
                            blockStore.setChainHead(storedBlock);
                            
                            log.info("Successfully set chain head to checkpoint: height={}, hash={}", 
                                    selectedCheckpointBlockHeight, selectedCheckpointBlockHash);
                            
                            // Reset checkpoint values after use
                            selectedCheckpointTimestamp = -1;
                            selectedCheckpointBlockHeight = -1;
                            selectedCheckpointBlockHash = null;
                            selectedCheckpointVersion = 0;
                            selectedCheckpointPrevBlockHash = null;
                            selectedCheckpointMerkleRoot = null;
                            selectedCheckpointTime = 0;
                            selectedCheckpointBits = null;
                            selectedCheckpointNonce = 0;
                            
                            // Clear persisted checkpoint values
                            final android.content.SharedPreferences prefs = getSharedPreferences("blockchain_service", android.content.Context.MODE_PRIVATE);
                            prefs.edit().clear().apply();
                            log.info("Cleared persisted checkpoint values after successful initialization");
                        } catch (Exception e) {
                            log.error("Error setting custom checkpoint as chain head: {}", e.getMessage(), e);
                            // Reset on error
                            selectedCheckpointBlockHeight = -1;
                            selectedCheckpointBlockHash = null;
                        }
                    }
                } catch (final BlockStoreException x) {
                    blockChainFile.delete();

                    final String msg = "blockstore cannot be created";
                    log.error(msg, x);
                    throw new Error(msg, x);
                }

                try {
                    blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, blockStore);
                } catch (final BlockStoreException x) {
                    throw new Error("blockchain cannot be created", x);
                }

                observeLiveDatasThatAreDependentOnWalletAndBlockchain();
            }
        });
    }

    private void observeLiveDatasThatAreDependentOnWalletAndBlockchain() {
        final NewTransactionLiveData newTransaction = new NewTransactionLiveData(wallet.getValue());
        newTransaction.observe(this, tx -> {
            final Wallet wallet = BlockchainService.this.wallet.getValue();
            postDelayedStopSelf(5 * DateUtils.MINUTE_IN_MILLIS);
            final Coin amount = tx.getValue(wallet);
            if (amount.isPositive()) {
                final Address address = WalletUtils.getWalletAddressOfReceived(tx, wallet);
                final ConfidenceType confidenceType = tx.getConfidence().getConfidenceType();
                // Check if service is being destroyed before accessing blockChain
                if (isDestroying || blockChain == null) {
                    return;
                }
                
                try {
                    final boolean replaying = blockChain.getBestChainHeight() < config.getBestChainHeightEver();
                    final boolean isReplayedTx = confidenceType == ConfidenceType.BUILDING && replaying;
                    if (!isReplayedTx)
                        notifyCoinsReceived(address, amount, tx.getTxId());
                } catch (Exception e) {
                    log.warn("Exception while processing transaction notification (service may be shutting down): {}", e.getMessage());
                }
            }
        });
        impediments = new ImpedimentsLiveData(application);
        impediments.observe(this, new Observer<Set<Impediment>>() {
            @Override
            public void onChanged(final Set<Impediment> impediments) {
                if (impediments.isEmpty() && peerGroup == null && Constants.ENABLE_BLOCKCHAIN_SYNC)
                    startup();
                else if (!impediments.isEmpty() && peerGroup != null)
                    shutdown();
                broadcastBlockchainState();
            }

            private void startup() {
                final Wallet wallet = BlockchainService.this.wallet.getValue();

                // Check if service is being destroyed
                if (isDestroying || blockChain == null || blockStore == null) {
                    log.warn("Service is being destroyed, skipping startup");
                    return;
                }
                
                // consistency check
                try {
                    final int walletLastBlockSeenHeight = wallet.getLastBlockSeenHeight();
                    final int bestChainHeight = blockChain.getBestChainHeight();
                    if (walletLastBlockSeenHeight != -1 && walletLastBlockSeenHeight != bestChainHeight) {
                        final String message = "wallet/blockchain out of sync: " + walletLastBlockSeenHeight + "/"
                                + bestChainHeight;
                        log.error(message);
                        CrashReporter.saveBackgroundTrace(new RuntimeException(message), application.packageInfo());
                    }
                } catch (Exception e) {
                    log.warn("Exception during consistency check (service may be shutting down): {}", e.getMessage());
                    return;
                }

                final Configuration.SyncMode syncMode = config.getSyncMode();
                peerGroup = new NonWitnessPeerGroup(Constants.NETWORK_PARAMETERS, blockChain);
                log.info("creating {}, sync mode: {}", peerGroup, syncMode);
                peerGroup.setDownloadTxDependencies(0); // recursive implementation causes StackOverflowError
                peerGroup.addWallet(wallet);
                peerGroup.setBloomFilteringEnabled(syncMode == Configuration.SyncMode.CONNECTION_FILTER);
                peerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
                peerGroup.addConnectedEventListener(peerConnectivityListener);
                peerGroup.addDisconnectedEventListener(peerConnectivityListener);

                final int maxConnectedPeers = application.maxConnectedPeers();
                final Set<HostAndPort> trustedPeers = config.getTrustedPeers();
                final boolean trustedPeerOnly = config.isTrustedPeersOnly();

                // Ensure minimum peer connections for mempool monitoring
                final int minPeersForMempool = 2;
                final int effectiveMaxPeers = Math.max(maxConnectedPeers, minPeersForMempool);
                peerGroup.setMaxConnections(trustedPeerOnly ? 0 : effectiveMaxPeers);
                peerGroup.setConnectTimeoutMillis(Constants.PEER_TIMEOUT_MS);
                peerGroup.setPeerDiscoveryTimeoutMillis(Constants.PEER_DISCOVERY_TIMEOUT_MS);
                peerGroup.setStallThreshold(20, Block.HEADER_SIZE * 10);
                
                // Enhanced peer connection settings for mempool monitoring
                peerGroup.setMinBroadcastConnections(minPeersForMempool);
                peerGroup.setBloomFilteringEnabled(true); // Enable bloom filtering for mempool monitoring

                final ResolveDnsTask resolveDnsTask = new ResolveDnsTask(backgroundHandler) {
                    @Override
                    protected void onSuccess(final HostAndPort hostAndPort, final InetSocketAddress socketAddress) {
                        log.info("trusted peer '{}' resolved to {}", hostAndPort,
                                socketAddress.getAddress().getHostAddress());
                        if (socketAddress != null) {
                            peerGroup.addAddress(new PeerAddress(Constants.NETWORK_PARAMETERS, socketAddress), 10);
                            if (peerGroup.getMaxConnections() > maxConnectedPeers)
                                peerGroup.setMaxConnections(maxConnectedPeers);
                        }
                    }

                    @Override
                    protected void onUnknownHost(final HostAndPort hostAndPort) {
                        log.info("trusted peer '{}' unknown host", hostAndPort);
                    }
                };
                for (final HostAndPort trustedPeer : trustedPeers)
                    resolveDnsTask.resolve(trustedPeer);

                if (trustedPeerOnly) {
                    log.info("trusted peers only – not adding any random nodes from the P2P network");
                } else {
                    log.info("adding random peers from the P2P network");
                    if (syncMode == Configuration.SyncMode.CONNECTION_FILTER)
                        peerGroup.setRequiredServices(VersionMessage.NODE_BLOOM);
                    else
                        peerGroup.setRequiredServices(0);
                }

                // start peergroup
                log.info("starting {} asynchronously", peerGroup);
                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(blockchainDownloadListener);
                
                // Start continuous mempool monitoring
                handler.postDelayed(() -> ensureMempoolMonitoring(), 3000); // Initial check after 3 seconds
                handler.postDelayed(mempoolMonitoringTask, 10000); // Start continuous monitoring after 10 seconds

                // Don't auto-stop when mempool monitoring is active - keep service running for background monitoring
                // postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS);
            }

            private void shutdown() {
                final Wallet wallet = BlockchainService.this.wallet.getValue();

                try {
                    peerGroup.removeDisconnectedEventListener(peerConnectivityListener);
                    peerGroup.removeConnectedEventListener(peerConnectivityListener);
                    peerGroup.removeWallet(wallet);
                    
                    // Only stop if the peer group is running
                    if (peerGroup.isRunning()) {
                        log.info("stopping {} asynchronously", peerGroup);
                        peerGroup.stopAsync();
                    } else {
                        log.info("peer group already stopped or not running: {}", peerGroup);
                    }
                } catch (Exception e) {
                    log.warn("Error stopping peer group in shutdown: {}", e.getMessage());
                } finally {
                    peerGroup = null;
                }
            }
        });
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);
        // Don't auto-stop for mempool monitoring - keep service running for background notifications
        // postDelayedStopSelf(DateUtils.MINUTE_IN_MILLIS * 2);

        if (intent != null) {
            final String action = intent.getAction();
            log.info("service start command: {}", action);

            if (BlockchainService.ACTION_CANCEL_COINS_RECEIVED.equals(action)) {
                notificationCount = 0;
                notificationAccumulatedAmount = Coin.ZERO;
                notificationAddresses.clear();

                nm.cancel(Constants.NOTIFICATION_ID_COINS_RECEIVED);
            } else if (BlockchainService.ACTION_RESET_BLOCKCHAIN.equals(action)) {
                log.info("will remove blockchain on service shutdown");
                resetBlockchainOnShutdown = true;
                
                // Extract checkpoint parameters from intent
                if (intent.hasExtra("checkpoint_timestamp")) {
                    selectedCheckpointTimestamp = intent.getLongExtra("checkpoint_timestamp", -1);
                }
                if (intent.hasExtra("checkpoint_block_height")) {
                    selectedCheckpointBlockHeight = intent.getIntExtra("checkpoint_block_height", -1);
                }
                if (intent.hasExtra("checkpoint_block_hash")) {
                    selectedCheckpointBlockHash = intent.getStringExtra("checkpoint_block_hash");
                    selectedCheckpointVersion = intent.getIntExtra("checkpoint_version", 0);
                    selectedCheckpointPrevBlockHash = intent.getStringExtra("checkpoint_prev_block_hash");
                    selectedCheckpointMerkleRoot = intent.getStringExtra("checkpoint_merkle_root");
                    selectedCheckpointTime = intent.getLongExtra("checkpoint_time", 0);
                    selectedCheckpointBits = intent.getStringExtra("checkpoint_bits");
                    selectedCheckpointNonce = intent.getLongExtra("checkpoint_nonce", 0);
                    
                    // Persist checkpoint values to SharedPreferences for service restart
                    final android.content.SharedPreferences prefs = getSharedPreferences("blockchain_service", android.content.Context.MODE_PRIVATE);
                    prefs.edit()
                        .putLong("checkpoint_timestamp", selectedCheckpointTimestamp)
                        .putInt("checkpoint_block_height", selectedCheckpointBlockHeight)
                        .putString("checkpoint_block_hash", selectedCheckpointBlockHash)
                        .putInt("checkpoint_version", selectedCheckpointVersion)
                        .putString("checkpoint_prev_block_hash", selectedCheckpointPrevBlockHash)
                        .putString("checkpoint_merkle_root", selectedCheckpointMerkleRoot)
                        .putLong("checkpoint_time", selectedCheckpointTime)
                        .putString("checkpoint_bits", selectedCheckpointBits)
                        .putLong("checkpoint_nonce", selectedCheckpointNonce)
                        .apply();
                    
                    log.info("Stored checkpoint values: height={}, hash={}", selectedCheckpointBlockHeight, selectedCheckpointBlockHash);
                }
                
                stopSelf();
                if (isBound.get())
                    log.info("stop is deferred because service still bound");
            }
        } else {
            log.warn("service restart, although it was started as non-sticky");
        }

        return START_STICKY; // Keep service running in background for peer connections
    }

    @Override
    public void onDestroy() {
        log.debug(".onDestroy()");

        // Set flag to prevent new operations on BlockStore
        isDestroying = true;

        if (peerGroup != null) {
            try {
                peerGroup.removeDisconnectedEventListener(peerConnectivityListener);
                peerGroup.removeConnectedEventListener(peerConnectivityListener);
                peerGroup.removeWallet(wallet.getValue());
                
                // Only stop if the peer group is running
                if (peerGroup.isRunning()) {
                    peerGroup.stopAsync();
                    log.info("stopping {} asynchronously", peerGroup);
                    
                    // Wait for peer group to stop (with timeout to prevent blocking)
                    try {
                        // Wait up to 5 seconds for peer group to stop
                        int attempts = 0;
                        while (peerGroup.isRunning() && attempts < 50) {
                            Thread.sleep(100);
                            attempts++;
                        }
                        if (peerGroup.isRunning()) {
                            log.warn("Peer group did not stop within timeout, proceeding with shutdown");
                        } else {
                            log.info("Peer group stopped successfully");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting for peer group to stop", e);
                    }
                } else {
                    log.info("peer group already stopped or not running: {}", peerGroup);
                }
            } catch (Exception e) {
                log.warn("Error stopping peer group: {}", e.getMessage());
            }
        }

        peerConnectivityListener.stop();

        delayHandler.removeCallbacksAndMessages(null);

        backgroundHandler.removeCallbacksAndMessages(null);
        backgroundThread.getLooper().quit();

        if (blockStore != null) {
            try {
                blockStore.close();
                log.info("BlockStore closed successfully");
            } catch (final BlockStoreException x) {
                log.error("Error closing BlockStore", x);
                // Don't throw RuntimeException - just log the error to prevent crash
            }
        }

        application.autosaveWalletNow();

        if (resetBlockchainOnShutdown) {
            log.info("removing blockchain");
            blockChainFile.delete();
        }

        unregisterReceiver(deviceIdleModeReceiver);

        config.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        
        // Stop worldwide peer discovery
        if (worldwidePeerDiscovery != null) {
            worldwidePeerDiscovery.stop();
        }

        final boolean expectLargeData =
                blockChain != null && (config.getBestChainHeightEver() - blockChain.getBestChainHeight()) > CONNECTIVITY_NOTIFICATION_PROGRESS_MIN_BLOCKS;
        StartBlockchainService.schedule(application, expectLargeData);

        wakeLock.release();
        log.info("released {}", wakeLock);
        checkState(!wakeLock.isHeld(), "still held: " + wakeLock);

        super.onDestroy();

        log.info("service was up for {}", serviceUpTime.stop());
    }

    @Override
    public void onTrimMemory(final int level) {
        log.info("onTrimMemory({}) called", level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            log.warn("low memory detected, trying to stop");
            
            // Set destroying flag to prevent new operations
            isDestroying = true;
            
            // Cancel all scheduled tasks immediately
            delayHandler.removeCallbacksAndMessages(null);
            backgroundHandler.removeCallbacksAndMessages(null);
            handler.removeCallbacksAndMessages(null);
            
            // Stop peer group if running
            if (peerGroup != null && peerGroup.isRunning()) {
                try {
                    peerGroup.stopAsync();
                } catch (Exception e) {
                    log.warn("Error stopping peer group during memory trim: {}", e.getMessage());
                }
            }
            
            stopSelf();
            if (isBound.get())
                log.info("stop is deferred because service still bound");
        }
    }

    @Nullable
    public TransactionBroadcast broadcastTransaction(final Transaction tx) {
        if (peerGroup != null) {
            log.info("broadcasting transaction {}", tx.getTxId());
            return peerGroup.broadcastTransaction(tx);
        } else {
            log.info("peergroup not available, not broadcasting transaction {}", tx.getTxId());
            return null;
        }
    }

    @Nullable
    public BlockchainState getBlockchainState() {
        if (isDestroying || blockChain == null || blockStore == null)
            return null;

        try {
            final StoredBlock chainHead = blockChain.getChainHead();
            final Date bestChainDate = chainHead.getHeader().getTime();
            final int bestChainHeight = chainHead.getHeight();
            final boolean replaying = chainHead.getHeight() < config.getBestChainHeightEver();

            return new BlockchainState(bestChainDate, bestChainHeight, replaying, impediments.getValue());
        } catch (Exception e) {
            log.warn("Exception while getting blockchain state (service may be shutting down): {}", e.getMessage());
            return null;
        }
    }

    @Nullable
    public List<Peer> getConnectedPeers() {
        if (peerGroup == null)
            return null;

        return peerGroup.getConnectedPeers();
    }

    /**
     * Get the total number of available peers discovered worldwide
     * This uses the independent DNS seed discovery service
     */
    public int getTotalDiscoveredPeers() {
        if (worldwidePeerDiscovery != null) {
            return worldwidePeerDiscovery.getCurrentWorldwidePeerCount();
        }
        
        // Fallback to connected peers if worldwide discovery not available
        if (peerGroup != null) {
            return peerGroup.getConnectedPeers().size();
        }
        
        return 0;
    }
    
    /**
     * Get the worldwide peer discovery service
     */
    public WorldwidePeerDiscovery getWorldwidePeerDiscovery() {
        return worldwidePeerDiscovery;
    }

    public void dropAllPeers() {
        if (peerGroup == null)
            return;
        peerGroup.dropAllPeers();
    }

    @Nullable
    public List<StoredBlock> getRecentBlocks(final int maxBlocks) {
        if (blockChain == null || blockStore == null)
            return null;

        final List<StoredBlock> blocks = new ArrayList<>(maxBlocks);
        StoredBlock block = blockChain.getChainHead();
        while (block != null) {
            blocks.add(block);
            if (blocks.size() >= maxBlocks)
                break;
            try {
                block = block.getPrev(blockStore);
            } catch (final BlockStoreException x) {
                log.info("skipping blocks because of exception", x);
                break;
            }
        }
        return blocks.isEmpty() ? null : blocks;
    }

    private void startForeground(final int numPeers) {
        if (config.isTrustedPeersOnly()) {
            connectivityNotification.setSmallIcon(R.drawable.stat_notify_peers, numPeers > 0 ? 4 : 0);
            connectivityNotification.setContentText(getString(numPeers > 0 ? R.string.notification_peer_connected :
                    R.string.notification_peer_not_connected));
        } else {
            connectivityNotification.setSmallIcon(R.drawable.stat_notify_peers, Math.min(numPeers, 4));
            connectivityNotification.setContentText(getString(R.string.notification_peers_connected_msg, numPeers));
        }
        try {
            startForeground(Constants.NOTIFICATION_ID_CONNECTIVITY, connectivityNotification.build());
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            log.warn("Cannot update foreground service from background: {}", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Cannot update foreground service: {}", e.getMessage());
        }
    }

    private void startForegroundProgress(final int blocksToDownload, final int blocksLeft) {
        connectivityNotification.setProgress(blocksToDownload, blocksToDownload - blocksLeft, false);
        try {
            startForeground(Constants.NOTIFICATION_ID_CONNECTIVITY, connectivityNotification.build());
        } catch (android.app.ForegroundServiceStartNotAllowedException e) {
            log.warn("Cannot update foreground service progress from background: {}", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Cannot update foreground service progress: {}", e.getMessage());
        }
    }

    @MainThread
    private void broadcastPeerState(final int numPeers) {
        application.peerState.setValue(numPeers);
        // Update worldwide peer count from independent discovery service
        if (worldwidePeerDiscovery != null) {
            final Integer worldwideCount = worldwidePeerDiscovery.worldwidePeerCount.getValue();
            if (worldwideCount != null) {
                application.totalDiscoveredPeers.setValue(worldwideCount);
            }
        }
    }

    @MainThread
    private void broadcastBlockchainState() {
        final BlockchainState blockchainState = getBlockchainState();
        application.blockchainState.setValue(blockchainState);
    }
    
    /**
     * Ensures minimum peer connections for mempool monitoring
     * This is crucial for detecting incoming Dogecoin transactions
     */
    public void ensureMempoolMonitoring() {
        if (peerGroup == null) {
            log.warn("PeerGroup is null, cannot ensure mempool monitoring");
            return;
        }
            
        final int connectedPeers = peerGroup.getConnectedPeers().size();
        final int minPeersForMempool = 2;
        
        log.info("Mempool monitoring check: {} connected peers (minimum: {})", connectedPeers, minPeersForMempool);
        
        if (connectedPeers < minPeersForMempool) {
            log.warn("Insufficient peers for mempool monitoring ({} < {}), attempting to connect more", 
                connectedPeers, minPeersForMempool);
            
            // Try to connect to more peers
            if (!peerGroup.isRunning()) {
                try {
                    log.info("Starting peer group for mempool monitoring");
                    peerGroup.startAsync();
                } catch (IllegalStateException e) {
                    log.warn("Peer group already starting or started: {}", e.getMessage());
                    // Peer group is already starting or started, which is fine
                } catch (Exception e) {
                    log.error("Unexpected error starting peer group: {}", e.getMessage());
                }
            } else {
                log.info("Peer group already running, ensuring it stays active for mempool monitoring");
                // Just ensure the peer group is active
                if (peerGroup.getMaxConnections() > connectedPeers) {
                    log.info("Peer group has capacity for more connections, waiting for peers to connect");
                }
            }
            
            // Schedule a check to ensure we maintain connections
            handler.postDelayed(() -> {
                if (peerGroup == null) {
                    log.warn("Peer group is null, skipping mempool monitoring check");
                    return;
                }
                final int newPeerCount = peerGroup.getConnectedPeers().size();
                if (newPeerCount < minPeersForMempool) {
                    log.warn("Still insufficient peers for mempool monitoring: {} (peer group running: {})", 
                        newPeerCount, peerGroup.isRunning());
                    // If still insufficient and no peers at all, try restarting the peer group
                    if (newPeerCount == 0 && !isRestarting) {
                        log.info("No peers connected, attempting to restart peer group");
                        isRestarting = true;
                        try {
                            // Only restart if the peer group is actually running
                            if (peerGroup.isRunning()) {
                                log.info("Stopping peer group for restart");
                                peerGroup.stopAsync();
                                handler.postDelayed(() -> {
                                    try {
                                        // Double-check the peer group state before starting
                                        if (!peerGroup.isRunning()) {
                                            log.info("Starting peer group after restart");
                                            peerGroup.startAsync();
                                        } else {
                                            log.info("Peer group already running after stop, no need to start");
                                        }
                                    } catch (IllegalStateException e) {
                                        log.warn("Peer group already starting or started during restart: {}", e.getMessage());
                                    } catch (Exception e) {
                                        log.error("Unexpected error starting peer group after restart: {}", e.getMessage());
                                    } finally {
                                        isRestarting = false;
                                    }
                                }, 2000);
                            } else {
                                // Peer group is not running, just start it
                                try {
                                    log.info("Starting peer group (not running)");
                                    peerGroup.startAsync();
                                } catch (IllegalStateException e) {
                                    log.warn("Peer group already starting or started: {}", e.getMessage());
                                } catch (Exception e) {
                                    log.error("Unexpected error starting peer group: {}", e.getMessage());
                                } finally {
                                    isRestarting = false;
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error during peer group restart: {}", e.getMessage());
                            isRestarting = false;
                        }
                    } else if (isRestarting) {
                        log.info("Peer group restart already in progress, skipping");
                    }
                } else {
                    log.info("Mempool monitoring now active with {} peers", newPeerCount);
                }
            }, 15000); // Check after 15 seconds
        } else {
            log.info("Mempool monitoring active with {} peers", connectedPeers);
        }
    }
    
    /**
     * Create a PendingIntent that uses the same launcher intent format as the app icon
     * This ensures notification clicks behave exactly like app icon clicks and open the wallet
     * (not SendCoinsActivity). The launcher intent is handled by WalletActivity.onCreate()
     * which routes through BiometricAuthActivity if needed.
     */
    private static PendingIntent createLauncherPendingIntent(Context context, int requestCode) {
        // Use the same intent format as app icon launch (ACTION_MAIN + CATEGORY_LAUNCHER)
        // This will be handled by WalletActivity.onCreate() exactly like app icon clicks
        // and will require biometric authentication if enabled
        Intent launcherIntent = new Intent(context, WalletActivity.class);
        launcherIntent.setAction(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(context, requestCode, launcherIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
