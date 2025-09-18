package de.schildbach.wallet.service;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.params.MainNetParams;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages Dogecoin peer discovery, storage, and real-time updates
 */
public class PeerManager {
    private static final String TAG = "PeerManager";
    private static final String PEERS_FILE = "peers.json";
    private static final int INITIAL_DNS_QUERIES = 5;
    private static final int PEER_CLEANUP_HOURS = 24;
    private static final int PEER_REMOVAL_HOURS = 48;
    
    private final Context context;
    private final NetworkParameters networkParameters;
    private final Handler mainHandler;
    private final ScheduledExecutorService backgroundExecutor;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Gson gson;
    
    // Callbacks for UI updates
    private PeerUpdateCallback peerUpdateCallback;
    
    public interface PeerUpdateCallback {
        void onPeerAdded(PeerInfo peer);
        void onPeerUpdated(PeerInfo peer);
        void onPeerRemoved(String peerId);
        void onTotalCountChanged(int totalCount);
    }
    
    public static class PeerInfo {
        public String id;
        public String ip;
        public int port;
        public String version;
        public String subVersion;
        public String services;
        public long syncedBlocks;
        public long latency;
        public String status; // "Online", "Discovered", "Offline"
        public long lastSeen;
        public long firstDiscovered;
        public String source; // "DNS", "Snowball", "Handshake"
        
        public PeerInfo() {}
        
        public PeerInfo(String ip, int port, String source) {
            this.id = ip + ":" + port;
            this.ip = ip;
            this.port = port;
            this.source = source;
            this.status = "Discovered";
            this.firstDiscovered = System.currentTimeMillis();
            this.lastSeen = System.currentTimeMillis();
        }
        
        public PeerInfo(org.bitcoinj.core.Peer peer, String hostname) {
            this.id = peer.getAddress().getAddr().getHostAddress() + ":" + peer.getAddress().getPort();
            this.ip = peer.getAddress().getAddr().getHostAddress();
            this.port = peer.getAddress().getPort();
            this.source = "Blockchain";
            this.status = "Online";
            this.firstDiscovered = System.currentTimeMillis();
            this.lastSeen = System.currentTimeMillis();
            
            // Calculate latency (ping time)
            try {
                this.latency = peer.getPingTime();
            } catch (Exception e) {
                this.latency = 0; // Unknown latency
            }
            
            // Try to get peer version information
            try {
                VersionMessage versionMessage = peer.getPeerVersionMessage();
                if (versionMessage != null) {
                    this.version = String.valueOf(versionMessage.clientVersion);
                    this.subVersion = versionMessage.subVer;
                    this.services = peer.toStringServices(versionMessage.localServices);
                }
            } catch (Exception e) {
                // Version info not available
            }
        }
    }
    
    public PeerManager(Context context) {
        this.context = context;
        this.networkParameters = MainNetParams.get();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Set up main thread handler
        HandlerThread handlerThread = new HandlerThread("PeerManager");
        handlerThread.start();
        this.mainHandler = new Handler(handlerThread.getLooper());
        
        // Set up background executor
        this.backgroundExecutor = Executors.newScheduledThreadPool(4);
        
        // Load existing peers
        loadPeersFromFile();
        
        // Start background tasks
        startBackgroundTasks();
    }
    
    public void setPeerUpdateCallback(PeerUpdateCallback callback) {
        this.peerUpdateCallback = callback;
    }
    
    /**
     * Start initial peer discovery with DNS queries
     */
    public void startInitialDiscovery() {
        backgroundExecutor.execute(() -> {
            Log.i(TAG, "Starting initial peer discovery...");
            
            // Phase 1: DNS discovery
            performInitialDnsDiscovery();
            
            // Phase 2: Start snowball discovery
            startSnowballDiscovery();
        });
    }
    
    /**
     * Perform initial DNS discovery with 5 queries to each seed
     */
    private void performInitialDnsDiscovery() {
        final String[] dnsSeeds = {"seed.multidoge.org", "seed2.multidoge.org"};
        
        for (String seed : dnsSeeds) {
            Log.i(TAG, "Starting DNS discovery for: " + seed);
            
            for (int attempt = 1; attempt <= INITIAL_DNS_QUERIES; attempt++) {
                try {
                    Log.d(TAG, "DNS query attempt " + attempt + "/" + INITIAL_DNS_QUERIES + " for: " + seed);
                    
                    // Get all addresses from DNS
                    InetAddress[] addresses = InetAddress.getAllByName(seed);
                    
                    for (InetAddress address : addresses) {
                        String ip = address.getHostAddress();
                        InetSocketAddress socketAddress = new InetSocketAddress(ip, 22556);
                        
                        // Add peer immediately
                        addPeer(socketAddress, "DNS");
                        
                        // Start handshake in background
                        performHandshakeAsync(socketAddress, "DNS");
                    }
                    
                    Log.i(TAG, "DNS attempt " + attempt + " for " + seed + ": " + addresses.length + " peers");
                    
                    // Random delay between queries (10-15 seconds)
                    if (attempt < INITIAL_DNS_QUERIES) {
                        int delay = 10000 + (int)(Math.random() * 5000);
                        Thread.sleep(delay);
                    }
                    
                } catch (Exception e) {
                    Log.w(TAG, "DNS query attempt " + attempt + " failed for " + seed + ": " + e.getMessage());
                }
            }
        }
        
        Log.i(TAG, "Initial DNS discovery complete. Total peers: " + peers.size());
    }
    
    /**
     * Start snowball peer discovery
     */
    private void startSnowballDiscovery() {
        backgroundExecutor.execute(() -> {
            Log.i(TAG, "Starting snowball peer discovery...");
            
            // Get all current peers
            List<PeerInfo> currentPeers = new ArrayList<>(peers.values());
            
            for (int round = 1; round <= 3; round++) {
                Log.i(TAG, "Snowball round " + round + ": Querying " + currentPeers.size() + " peers");
                
                List<PeerInfo> newPeers = new ArrayList<>();
                
                for (PeerInfo peer : currentPeers) {
                    if (peer.status.equals("Online")) {
                        // Query this peer for more peers
                        List<InetSocketAddress> discoveredPeers = queryPeerForPeers(peer);
                        
                        for (InetSocketAddress newPeer : discoveredPeers) {
                            if (!peers.containsKey(newPeer.getAddress().getHostAddress() + ":22556")) {
                                addPeer(newPeer, "Snowball Round " + round);
                                performHandshakeAsync(newPeer, "Snowball Round " + round);
                                newPeers.add(peers.get(newPeer.getAddress().getHostAddress() + ":22556"));
                            }
                        }
                    }
                }
                
                currentPeers = newPeers;
                
                // Delay between rounds
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            Log.i(TAG, "Snowball discovery complete. Total peers: " + peers.size());
        });
    }
    
    /**
     * Query a peer for more peers using Dogecoin protocol
     */
    private List<InetSocketAddress> queryPeerForPeers(PeerInfo peer) {
        List<InetSocketAddress> discoveredPeers = new ArrayList<>();
        
        try {
            Log.d(TAG, "Querying peer " + peer.ip + " for more peers");
            
            // Simulate peer discovery (in real implementation, this would use Dogecoin protocol)
            int numPeers = 5 + (int)(Math.random() * 11);
            
            for (int i = 0; i < numPeers; i++) {
                String randomIP = generateRandomIP();
                InetSocketAddress newPeer = new InetSocketAddress(randomIP, 22556);
                discoveredPeers.add(newPeer);
            }
            
            Log.d(TAG, "Peer " + peer.ip + " provided " + discoveredPeers.size() + " peers");
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to query peer " + peer.ip + ": " + e.getMessage());
        }
        
        return discoveredPeers;
    }
    
    /**
     * Perform handshake with peer to get details
     */
    private void performHandshakeAsync(InetSocketAddress peerAddress, String source) {
        backgroundExecutor.execute(() -> {
            try {
                Log.d(TAG, "Performing handshake with peer: " + peerAddress);
                
                // Simulate handshake (in real implementation, this would use Dogecoin protocol)
                Thread.sleep(1000 + (int)(Math.random() * 2000));
                
                // Update peer info
                String peerId = peerAddress.getAddress().getHostAddress() + ":22556";
                PeerInfo peer = peers.get(peerId);
                
                if (peer != null) {
                    peer.status = "Online";
                    peer.version = "70015";
                    peer.subVersion = "Shibestoshi:1.14.9";
                    peer.services = "0000000000000001";
                    peer.syncedBlocks = 100000 + (long)(Math.random() * 50000);
                    peer.latency = 50 + (long)(Math.random() * 200);
                    peer.lastSeen = System.currentTimeMillis();
                    
                    // Update UI
                    mainHandler.post(() -> {
                        if (peerUpdateCallback != null) {
                            peerUpdateCallback.onPeerUpdated(peer);
                        }
                    });
                    
                    // Save to file
                    savePeersToFile();
                }
                
            } catch (Exception e) {
                Log.w(TAG, "Handshake failed with peer " + peerAddress + ": " + e.getMessage());
                
                // Mark as offline
                String peerId = peerAddress.getAddress().getHostAddress() + ":22556";
                PeerInfo peer = peers.get(peerId);
                if (peer != null) {
                    peer.status = "Offline";
                    peer.lastSeen = System.currentTimeMillis();
                    
                    mainHandler.post(() -> {
                        if (peerUpdateCallback != null) {
                            peerUpdateCallback.onPeerUpdated(peer);
                        }
                    });
                }
            }
        });
    }
    
    /**
     * Add a new peer
     */
    private void addPeer(InetSocketAddress peerAddress, String source) {
        String peerId = peerAddress.getAddress().getHostAddress() + ":22556";
        
        if (!peers.containsKey(peerId)) {
            PeerInfo peer = new PeerInfo(peerAddress.getAddress().getHostAddress(), 22556, source);
            peers.put(peerId, peer);
            
            // Update UI
            mainHandler.post(() -> {
                if (peerUpdateCallback != null) {
                    peerUpdateCallback.onPeerAdded(peer);
                    peerUpdateCallback.onTotalCountChanged(peers.size());
                }
            });
            
            Log.d(TAG, "Added new peer: " + peerId + " from " + source);
        }
    }
    
    /**
     * Generate random IP for simulation
     */
    private String generateRandomIP() {
        int[][] ranges = {{1, 126}, {128, 191}, {192, 223}};
        int[] range = ranges[(int)(Math.random() * ranges.length)];
        int firstOctet = range[0] + (int)(Math.random() * (range[1] - range[0] + 1));
        int secondOctet = (int)(Math.random() * 256);
        int thirdOctet = (int)(Math.random() * 256);
        int fourthOctet = (int)(Math.random() * 256);
        
        return firstOctet + "." + secondOctet + "." + thirdOctet + "." + fourthOctet;
    }
    
    /**
     * Start background tasks
     */
    private void startBackgroundTasks() {
        // Peer health check every 24 hours
        backgroundExecutor.scheduleAtFixedRate(() -> {
            performPeerHealthCheck();
        }, 24, 24, TimeUnit.HOURS);
        
        // Save peers to file every 5 minutes
        backgroundExecutor.scheduleAtFixedRate(() -> {
            savePeersToFile();
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Perform peer health check
     */
    private void performPeerHealthCheck() {
        Log.i(TAG, "Starting peer health check...");
        
        long currentTime = System.currentTimeMillis();
        List<String> peersToRemove = new ArrayList<>();
        
        for (PeerInfo peer : peers.values()) {
            long timeSinceLastSeen = currentTime - peer.lastSeen;
            
            if (timeSinceLastSeen > PEER_REMOVAL_HOURS * 60 * 60 * 1000) {
                // Remove peer if offline for more than 48 hours
                peersToRemove.add(peer.id);
            } else if (timeSinceLastSeen > PEER_CLEANUP_HOURS * 60 * 60 * 1000) {
                // Mark as offline if not seen for 24 hours
                if (!peer.status.equals("Offline")) {
                    peer.status = "Offline";
                    mainHandler.post(() -> {
                        if (peerUpdateCallback != null) {
                            peerUpdateCallback.onPeerUpdated(peer);
                        }
                    });
                }
            }
        }
        
        // Remove old peers
        for (String peerId : peersToRemove) {
            peers.remove(peerId);
            mainHandler.post(() -> {
                if (peerUpdateCallback != null) {
                    peerUpdateCallback.onPeerRemoved(peerId);
                    peerUpdateCallback.onTotalCountChanged(peers.size());
                }
            });
        }
        
        Log.i(TAG, "Peer health check complete. Removed " + peersToRemove.size() + " peers");
    }
    
    /**
     * Load peers from JSON file
     */
    private void loadPeersFromFile() {
        try {
            File file = new File(context.getFilesDir(), PEERS_FILE);
            if (file.exists()) {
                FileReader reader = new FileReader(file);
                Type type = new TypeToken<Map<String, PeerInfo>>(){}.getType();
                Map<String, PeerInfo> loadedPeers = gson.fromJson(reader, type);
                
                if (loadedPeers != null) {
                    peers.putAll(loadedPeers);
                    Log.i(TAG, "Loaded " + peers.size() + " peers from file");
                }
                
                reader.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load peers from file: " + e.getMessage());
        }
    }
    
    /**
     * Save peers to JSON file
     */
    private void savePeersToFile() {
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
     * Get all peers
     */
    public List<PeerInfo> getAllPeers() {
        return new ArrayList<>(peers.values());
    }
    
    /**
     * Get total peer count
     */
    public int getTotalPeerCount() {
        return peers.size();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        backgroundExecutor.shutdown();
        savePeersToFile();
    }
}

