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

import android.util.Log;
import de.schildbach.wallet.data.DogecoinPeer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles DNS-based peer discovery for Dogecoin seeds
 * 
 * @author AI Assistant
 */
public class DNSPeerDiscovery {
    private static final String TAG = "DNSPeerDiscovery";
    
    private static final String[] DNS_SEEDS = {
        "seed.multidoge.org",
        "seed2.multidoge.org"
    };
    
    private static final int DOGECOIN_PORT = 22556;
    private static final int DNS_QUERIES_PER_SEED = 4;
    private static final int DNS_QUERY_DELAY_MS = 500; // 0.5 seconds between queries for faster discovery
    
    private final ExecutorService executor;
    private final PeerStorageManager storageManager;
    private final PeerDiscoveryCallback callback;
    
    public interface PeerDiscoveryCallback {
        void onPeerDiscovered(DogecoinPeer peer);
        void onDiscoveryComplete(int totalPeers);
    }
    
    public DNSPeerDiscovery(PeerStorageManager storageManager, PeerDiscoveryCallback callback) {
        this.storageManager = storageManager;
        this.callback = callback;
        this.executor = Executors.newFixedThreadPool(2);
    }
    
    /**
     * Start DNS discovery process
     */
    public void startDiscovery() {
        executor.execute(() -> {
            Log.i(TAG, "Starting DNS peer discovery...");
            
            Set<String> discoveredPeers = new HashSet<>();
            int totalDiscovered = 0;
            
            for (String seed : DNS_SEEDS) {
                Log.i(TAG, "Discovering peers from seed: " + seed);
                
                for (int attempt = 1; attempt <= DNS_QUERIES_PER_SEED; attempt++) {
                    try {
                        Log.d(TAG, "DNS query attempt " + attempt + "/" + DNS_QUERIES_PER_SEED + " for: " + seed);
                        
                        // Perform DNS lookup
                        InetAddress[] addresses = InetAddress.getAllByName(seed);
                        
                        for (InetAddress address : addresses) {
                            String ip = address.getHostAddress();
                            String peerId = ip + ":" + DOGECOIN_PORT;
                            
                            // Only add if not already discovered
                            if (!discoveredPeers.contains(peerId)) {
                                DogecoinPeer peer = new DogecoinPeer(ip, DOGECOIN_PORT, "DNS");
                                peer.status = "Discovered";
                                
                                // Set basic information immediately for display
                                peer.version = 0; // Will be updated by handshake
                                peer.subVersion = "Unknown"; // Will be updated by handshake
                                peer.services = 0; // Will be updated by handshake
                                peer.syncedBlocks = 0; // Will be updated by handshake
                                peer.latency = -1; // Will be updated by handshake
                                peer.lastSeen = System.currentTimeMillis();
                                
                                // Add to storage
                                storageManager.addOrUpdatePeer(peer);
                                discoveredPeers.add(peerId);
                                totalDiscovered++;
                                
                                // Notify callback immediately for instant display
                                if (callback != null) {
                                    callback.onPeerDiscovered(peer);
                                }
                                
                                Log.d(TAG, "Discovered peer: " + peerId + " - showing immediately");
                            }
                        }
                        
                        Log.i(TAG, "DNS attempt " + attempt + " for " + seed + ": " + addresses.length + " addresses");
                        
                        // Delay between queries (except for last attempt)
                        if (attempt < DNS_QUERIES_PER_SEED) {
                            Thread.sleep(DNS_QUERY_DELAY_MS);
                        }
                        
                    } catch (Exception e) {
                        Log.w(TAG, "DNS query attempt " + attempt + " failed for " + seed + ": " + e.getMessage());
                    }
                }
                
                // Delay between seeds
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            Log.i(TAG, "DNS discovery complete. Total peers discovered: " + totalDiscovered);
            
            if (callback != null) {
                callback.onDiscoveryComplete(totalDiscovered);
            }
        });
    }
    
    /**
     * Shutdown the executor
     */
    public void shutdown() {
        executor.shutdown();
    }
}
