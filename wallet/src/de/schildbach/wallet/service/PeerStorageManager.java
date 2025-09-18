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
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.schildbach.wallet.data.DogecoinPeer;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages storage and retrieval of Dogecoin peers in peers.json file
 * 
 * @author AI Assistant
 */
public class PeerStorageManager {
    private static final String TAG = "PeerStorageManager";
    private static final String PEERS_FILE = "peers.json";
    private static final int MAX_PEERS = 1000; // Limit to prevent memory issues
    
    private final Context context;
    private final Gson gson;
    private final Map<String, DogecoinPeer> peers = new ConcurrentHashMap<>();
    
    public PeerStorageManager(Context context) {
        this.context = context;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        
        loadPeersFromFile();
        fixOldPeerSources();
    }
    
    /**
     * Fix old peer sources that might have incorrect values
     */
    private void fixOldPeerSources() {
        boolean needsSave = false;
        for (DogecoinPeer peer : peers.values()) {
            if ("WorldwideDiscovery".equals(peer.source)) {
                peer.source = "Peer";
                needsSave = true;
                Log.d(TAG, "Fixed peer source for " + peer.getAddress() + " from WorldwideDiscovery to Peer");
            }
        }
        if (needsSave) {
            savePeersToFile();
            Log.i(TAG, "Fixed old peer sources and saved to file");
        }
    }
    
    /**
     * Add a new peer or update existing one
     * Now respects manually updated peers to prevent overwrites
     */
    public void addOrUpdatePeer(DogecoinPeer peer) {
        String peerId = peer.getId();
        DogecoinPeer existingPeer = peers.get(peerId);
        
        if (existingPeer != null) {
            // Check if existing peer was manually updated
            if (existingPeer.isManuallyUpdated() && !peer.isManuallyUpdated()) {
                Log.d(TAG, "Skipping update for manually updated peer: " + peerId);
                return; // Don't overwrite manually updated peers
            }
            
            // Update existing peer
            existingPeer.version = peer.version;
            existingPeer.subVersion = peer.subVersion;
            existingPeer.services = peer.services;
            existingPeer.syncedBlocks = peer.syncedBlocks;
            existingPeer.latency = peer.latency;
            existingPeer.status = peer.status;
            existingPeer.lastSeen = peer.lastSeen;
            existingPeer.source = peer.source;
            existingPeer.country = peer.country;
            existingPeer.manuallyUpdated = peer.manuallyUpdated; // Preserve manual update flag
        } else {
            // Check memory limit before adding new peer
            if (peers.size() >= MAX_PEERS) {
                // Remove oldest peer to make room
                String oldestPeerId = findOldestPeer();
                if (oldestPeerId != null) {
                    peers.remove(oldestPeerId);
                    Log.d(TAG, "Removed oldest peer to maintain limit: " + oldestPeerId);
                }
            }
            
            // Add new peer
            peers.put(peerId, peer);
        }
        
        savePeersToFile();
        Log.d(TAG, "Added/Updated peer: " + peerId + " (Total: " + peers.size() + ")");
    }
    
    /**
     * Get all peers
     */
    public List<DogecoinPeer> getAllPeers() {
        List<DogecoinPeer> allPeers = new ArrayList<>(peers.values());
        Log.d(TAG, "getAllPeers() returning " + allPeers.size() + " peers");
        return allPeers;
    }
    
    /**
     * Get peers by status
     */
    public List<DogecoinPeer> getPeersByStatus(String status) {
        List<DogecoinPeer> result = new ArrayList<>();
        for (DogecoinPeer peer : peers.values()) {
            if (status.equals(peer.status)) {
                result.add(peer);
            }
        }
        return result;
    }
    
    /**
     * Get total peer count
     */
    public int getTotalPeerCount() {
        return peers.size();
    }
    
    /**
     * Get online peer count
     */
    public int getOnlinePeerCount() {
        return getPeersByStatus("Online").size();
    }
    
    /**
     * Check if peer exists
     */
    public boolean hasPeer(String ip, int port) {
        return peers.containsKey(ip + ":" + port);
    }
    
    /**
     * Get peer by IP and port
     */
    public DogecoinPeer getPeer(String ip, int port) {
        return peers.get(ip + ":" + port);
    }
    
    /**
     * Update peer status only: mark as offline after 48h, but don't remove any peers
     */
    public void updatePeerStatusOnly() {
        List<DogecoinPeer> peersToMarkOffline = new ArrayList<>();
        
        for (DogecoinPeer peer : peers.values()) {
            // Only mark peers as offline if they haven't responded for 48 hours
            if (peer.shouldBeMarkedOffline()) {
                peer.setOffline();
                peersToMarkOffline.add(peer);
            }
        }
        
        // Mark peers as offline
        for (DogecoinPeer peer : peersToMarkOffline) {
            addOrUpdatePeer(peer);
        }
        
        if (!peersToMarkOffline.isEmpty()) {
            Log.i(TAG, "Marked " + peersToMarkOffline.size() + " peers as offline");
        }
    }

    /**
     * Clean up old peers: mark as offline after 48h, remove after 7 days
     */
    public void cleanupOldPeers() {
        List<String> peersToRemove = new ArrayList<>();
        List<DogecoinPeer> peersToMarkOffline = new ArrayList<>();
        
        for (DogecoinPeer peer : peers.values()) {
            // First, mark peers as offline if they haven't responded for 48 hours
            if (peer.shouldBeMarkedOffline()) {
                peer.setOffline();
                peersToMarkOffline.add(peer);
            }
            // Then, remove peers that have been offline for 7 days
            else if (peer.shouldBeRemoved()) {
                peersToRemove.add(peer.getId());
            }
        }
        
        // Mark peers as offline
        for (DogecoinPeer peer : peersToMarkOffline) {
            addOrUpdatePeer(peer);
        }
        
        // Remove old offline peers
        for (String peerId : peersToRemove) {
            peers.remove(peerId);
        }
        
        if (!peersToMarkOffline.isEmpty() || !peersToRemove.isEmpty()) {
            savePeersToFile();
            Log.i(TAG, "Marked " + peersToMarkOffline.size() + " peers as offline (48h), removed " + 
                  peersToRemove.size() + " old peers (7 days)");
        }
    }
    
    /**
     * Load peers from JSON file
     */
    private void loadPeersFromFile() {
        try {
            File file = new File(context.getFilesDir(), PEERS_FILE);
            if (file.exists()) {
                FileReader reader = new FileReader(file);
                Type type = new TypeToken<Map<String, DogecoinPeer>>(){}.getType();
                Map<String, DogecoinPeer> loadedPeers = gson.fromJson(reader, type);
                
                if (loadedPeers != null) {
                    // Update lastSeen timestamp to prevent immediate cleanup
                    long now = System.currentTimeMillis();
                    for (DogecoinPeer peer : loadedPeers.values()) {
                        peer.lastSeen = now;
                    }
                    peers.putAll(loadedPeers);
                    Log.i(TAG, "Loaded " + peers.size() + " peers from file");
                }
                
                reader.close();
            } else {
                Log.i(TAG, "No peers file found, starting fresh");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load peers from file: " + e.getMessage());
        }
    }
    
    /**
     * Save peers to JSON file
     */
    public void savePeersToFile() {
        try {
            File file = new File(context.getFilesDir(), PEERS_FILE);
            FileWriter writer = new FileWriter(file);
            gson.toJson(peers, writer);
            writer.close();
            
            Log.d(TAG, "Saved " + peers.size() + " peers to file");
        } catch (Exception e) {
            Log.w(TAG, "Failed to save peers to file: " + e.getMessage());
        }
    }
    
    /**
     * Find the oldest peer to remove when memory limit is reached
     */
    private String findOldestPeer() {
        String oldestPeerId = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, DogecoinPeer> entry : peers.entrySet()) {
            DogecoinPeer peer = entry.getValue();
            if (peer.lastSeen < oldestTime) {
                oldestTime = peer.lastSeen;
                oldestPeerId = entry.getKey();
            }
        }
        
        return oldestPeerId;
    }
    
    /**
     * Clear all peers (for testing)
     */
    public void clearAllPeers() {
        try {
            peers.clear();
            savePeersToFile();
            Log.i(TAG, "Cleared all peers successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing peers: " + e.getMessage(), e);
            throw e;
        }
    }
}
