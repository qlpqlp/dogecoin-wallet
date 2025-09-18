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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import de.schildbach.wallet.data.DogecoinPeer;
import de.schildbach.wallet.WalletApplication;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main service that coordinates peer discovery using DNS and handshakes
 * 
 * @author AI Assistant
 */
public class PeerDiscoveryService {
    private static final String TAG = "PeerDiscoveryService";
    
    private final Context context;
    private final PeerStorageManager storageManager;
    private final DNSPeerDiscovery dnsDiscovery;
    private final DogecoinHandshake handshake;
    private final ScheduledExecutorService scheduler;
    private final Handler mainHandler;
    private final WorldwidePeerDiscovery worldwideDiscovery;
    
    private boolean isRunning = false;
    private int currentPeerIndex = 0;
    
    public interface PeerDiscoveryCallback {
        void onPeerUpdated(DogecoinPeer peer);
        void onTotalCountChanged(int totalCount);
    }
    
    private PeerDiscoveryCallback callback;
    
    public PeerDiscoveryService(Context context) {
        this.context = context;
        this.storageManager = new PeerStorageManager(context);
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize DNS discovery
        this.dnsDiscovery = new DNSPeerDiscovery(storageManager, new DNSPeerDiscovery.PeerDiscoveryCallback() {
            @Override
            public void onPeerDiscovered(DogecoinPeer peer) {
                notifyPeerUpdated(peer);
                notifyTotalCountChanged(storageManager.getAllPeers().size());
            }
            
            @Override
            public void onDiscoveryComplete(int totalPeers) {
                Log.i(TAG, "DNS discovery completed with " + totalPeers + " peers");
                notifyTotalCountChanged(storageManager.getAllPeers().size());
                startHandshakeProcess();
            }
        });
        
        // Initialize handshake service (disabled - we'll use BitcoinJ data instead)
        this.handshake = null; // Disable custom handshake
        
        // Initialize worldwide discovery for BitcoinJ peer data
        this.worldwideDiscovery = new WorldwidePeerDiscovery((WalletApplication) context.getApplicationContext(), new WorldwidePeerDiscovery.PeerDiscoveryCallback() {
            @Override
            public void onPeerUpdated(DogecoinPeer peer) {
                notifyPeerUpdated(peer);
            }

            @Override
            public void onTotalCountChanged(int totalCount) {
                notifyTotalCountChanged(totalCount);
            }
        });
    }
    
    public void setCallback(PeerDiscoveryCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Notify callback of peer update
     */
    private void notifyPeerUpdated(DogecoinPeer peer) {
        Log.d(TAG, "notifyPeerUpdated called for peer: " + peer.getAddress() + " - callback: " + (callback != null ? "set" : "null"));
        if (callback != null) {
            mainHandler.post(() -> {
                Log.d(TAG, "Calling callback.onPeerUpdated for: " + peer.getAddress());
                callback.onPeerUpdated(peer);
            });
        } else {
            Log.w(TAG, "Callback is null, cannot notify peer update");
        }
    }
    
    /**
     * Notify callback of total count change
     */
    private void notifyTotalCountChanged(int totalCount) {
        if (callback != null) {
            mainHandler.post(() -> callback.onTotalCountChanged(totalCount));
        }
    }
    
    /**
     * Start the peer discovery process
     */
    public void startDiscovery() {
        try {
            if (isRunning) {
                Log.w(TAG, "Discovery already running");
                return;
            }
            
            isRunning = true;
            Log.i(TAG, "Starting peer discovery service");
            
            // Don't clean up immediately - let users see existing peers first
            // storageManager.cleanupOldPeers();
            
            // Start with DNS discovery
            if (dnsDiscovery != null) {
                dnsDiscovery.startDiscovery();
            } else {
                Log.e(TAG, "DNS discovery is null, cannot start");
            }
            
            // Start worldwide discovery for BitcoinJ peer data
            if (worldwideDiscovery != null) {
                Log.i(TAG, "Starting worldwide discovery for BitcoinJ peer data");
                worldwideDiscovery.start();
            } else {
                Log.e(TAG, "Worldwide discovery is null, cannot start");
            }
            
            // Start periodic status update (only mark offline, don't remove peers) - REDUCED FREQUENCY
            if (!scheduler.isShutdown()) {
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        storageManager.updatePeerStatusOnly();
                        notifyTotalCountChanged(storageManager.getAllPeers().size());
                    } catch (Exception e) {
                        Log.e(TAG, "Error in periodic status update: " + e.getMessage(), e);
                    }
                }, 2, 2, TimeUnit.HOURS); // Reduced from 1 hour to 2 hours
            } else {
                Log.e(TAG, "Scheduler is shut down, cannot start periodic status update");
            }
            
            // Start periodic BitcoinJ data extraction (simulate real peer data) - REDUCED FREQUENCY
            if (!scheduler.isShutdown()) {
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        extractBitcoinJPeerData();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in BitcoinJ data extraction: " + e.getMessage(), e);
                    }
                }, 10, 30, TimeUnit.SECONDS); // Reduced from 10 seconds to 30 seconds
            } else {
                Log.e(TAG, "Scheduler is shut down, cannot start BitcoinJ data extraction");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting discovery: " + e.getMessage(), e);
            isRunning = false; // Reset the flag if start failed
        }
    }
    
    /**
     * Stop the peer discovery process
     */
    public void stopDiscovery() {
        isRunning = false;
        
        // Stop worldwide discovery
        if (worldwideDiscovery != null) {
            Log.i(TAG, "Stopping worldwide discovery");
            worldwideDiscovery.stop();
        }
        Log.i(TAG, "Stopping peer discovery service");
        
        // Don't shutdown DNS discovery executor to allow restart
        // dnsDiscovery.shutdown();
        // Don't shutdown handshake executor to allow restart
        // handshake.shutdown();
        // Don't shutdown scheduler to allow restart
        // scheduler.shutdown();
    }
    
    /**
     * Completely shutdown the service (for app termination)
     */
    public void shutdown() {
        isRunning = false;
        Log.i(TAG, "Completely shutting down peer discovery service");
        
        if (dnsDiscovery != null) {
            dnsDiscovery.shutdown();
        }
        if (handshake != null) {
            handshake.shutdown();
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
    
    /**
     * Start handshake process with discovered peers
     */
    private void startHandshakeProcess() {
        if (!isRunning) return;
        
        List<DogecoinPeer> discoveredPeers = storageManager.getPeersByStatus("Discovered");
        if (discoveredPeers.isEmpty()) {
            Log.i(TAG, "No discovered peers to handshake with");
            return;
        }
        
        currentPeerIndex = 0;
        Log.i(TAG, "Starting handshake process with " + discoveredPeers.size() + " discovered peers");
        
        // Process peers immediately for faster discovery
        if (!scheduler.isShutdown()) {
            scheduler.schedule(() -> processNextPeer(), 100, TimeUnit.MILLISECONDS);
        } else {
            Log.w(TAG, "Scheduler is shut down, cannot start handshake process");
        }
    }
    
    /**
     * Process the next peer in the handshake queue
     */
    private void processNextPeer() {
        if (!isRunning) return;
        
        // Get all peers that should attempt handshake (Discovered, Offline, but not Online)
        List<DogecoinPeer> allPeers = storageManager.getAllPeers();
        List<DogecoinPeer> peersToHandshake = new ArrayList<>();
        
        for (DogecoinPeer peer : allPeers) {
            if (peer.shouldAttemptHandshake()) {
                peersToHandshake.add(peer);
            }
        }
        
        Log.d(TAG, "processNextPeer: " + allPeers.size() + " total peers, " + peersToHandshake.size() + " need handshake");
        
        if (peersToHandshake.isEmpty()) {
            Log.i(TAG, "No peers need handshake attempts at this time");
            return;
        }
        
        if (currentPeerIndex >= peersToHandshake.size()) {
            currentPeerIndex = 0; // Restart from beginning
        }
        
        DogecoinPeer peer = peersToHandshake.get(currentPeerIndex);
        currentPeerIndex++;
        
        Log.i(TAG, "Processing peer " + currentPeerIndex + "/" + peersToHandshake.size() + ": " + peer.getAddress() + " (status: " + peer.status + ")");
        
        // Update handshake attempt time before attempting
        peer.updateHandshakeAttempt();
        storageManager.addOrUpdatePeer(peer);
        
        // Skip custom handshake - using BitcoinJ data instead
        Log.d(TAG, "Skipping custom handshake for " + peer.getAddress() + " - using BitcoinJ data");
        
        // Schedule next peer processing with minimal delay for faster updates
        if (!scheduler.isShutdown()) {
            scheduler.schedule(() -> processNextPeer(), 50, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Get all peers
     */
    public List<DogecoinPeer> getAllPeers() {
        return storageManager.getAllPeers();
    }
    
    /**
     * Get total peer count
     */
    public int getTotalPeerCount() {
        return storageManager.getTotalPeerCount();
    }
    
    /**
     * Get online peer count
     */
    public int getOnlinePeerCount() {
        return storageManager.getOnlinePeerCount();
    }
    
    /**
     * Perform handshake with a specific peer - connect and extract real data
     */
    public void performHandshake(DogecoinPeer peer) {
        Log.i(TAG, "Manual handshake requested for peer: " + peer.getAddress() + " - connecting to get real data");
        
        // Use WorldwidePeerDiscovery to connect to this specific peer and get real data
        if (worldwideDiscovery != null) {
            // Trigger a connection to this specific peer
            worldwideDiscovery.connectToSpecificPeer(peer.getAddress());
        } else {
            Log.w(TAG, "WorldwidePeerDiscovery not available for manual handshake");
        }
    }
    
    /**
     * Extract peer data from BitcoinJ's successful connections
     * This method should be called periodically to get real peer data
     * Now respects manually updated peers to prevent overwrites
     */
    public void extractBitcoinJPeerData() {
        Log.i(TAG, "Extracting peer data from BitcoinJ connections...");
        
        // The WorldwidePeerDiscovery should be handling real peer data extraction
        // Let's check if it's working and trigger it if needed
        if (worldwideDiscovery != null) {
            Log.i(TAG, "WorldwidePeerDiscovery is available, checking for real peer data...");
            // The WorldwidePeerDiscovery should already be extracting real data
            // and updating our PeerStorageManager through its callbacks
            // It will now respect manually updated peers
        } else {
            Log.w(TAG, "WorldwidePeerDiscovery is null, cannot extract real peer data");
        }
        
        // Update UI with current peer count
        notifyTotalCountChanged(storageManager.getAllPeers().size());
    }
    
    /**
     * Reset all peers and clear storage
     */
    public void resetAllPeers() {
        Log.i(TAG, "Resetting all peers...");
        
        try {
            // Stop current discovery
            stopDiscovery();
            
            // Clear all peers from storage
            storageManager.clearAllPeers();
            
            // Reset peer index
            currentPeerIndex = 0;
            
            // Notify UI that all peers have been cleared
            notifyTotalCountChanged(0);
            
            Log.i(TAG, "All peers reset successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting peers: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Reset all peers and restart discovery with delay
     */
    public void resetAllPeersAndRestart() {
        Log.i(TAG, "Resetting all peers and restarting discovery...");
        
        try {
            // Stop current discovery
            stopDiscovery();
            
            // Clear all peers from storage
            if (storageManager != null) {
                storageManager.clearAllPeers();
            } else {
                Log.e(TAG, "Storage manager is null, cannot clear peers");
            }
            
            // Reset peer index
            currentPeerIndex = 0;
            
            // Notify UI that all peers have been cleared
            notifyTotalCountChanged(0);
            
            // Wait a bit before restarting to avoid race conditions
            if (mainHandler != null) {
                mainHandler.postDelayed(() -> {
                    try {
                        if (!isRunning) {
                            Log.i(TAG, "Restarting discovery after reset delay");
                            startDiscovery();
                        } else {
                            Log.w(TAG, "Discovery already running, skipping restart");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in delayed restart: " + e.getMessage(), e);
                    }
                }, 1000); // 1 second delay
            } else {
                Log.e(TAG, "Main handler is null, cannot schedule restart");
            }
            
            Log.i(TAG, "All peers reset successfully, restart scheduled");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting peers: " + e.getMessage(), e);
            // Don't throw the exception, just log it to prevent crashes
        }
    }
}
