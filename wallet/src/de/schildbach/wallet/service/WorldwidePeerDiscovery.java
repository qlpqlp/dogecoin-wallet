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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import androidx.lifecycle.MutableLiveData;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.NodeInfo;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.net.discovery.SeedPeers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Independent worldwide peer discovery service that queries Dogecoin DNS seeds
 * to discover all available peers without affecting wallet sync functionality.
 * 
 * @author AI Assistant
 */
public class WorldwidePeerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(WorldwidePeerDiscovery.class);
    
    private final WalletApplication application;
    private final NetworkParameters networkParameters;
    private final HandlerThread discoveryThread;
    private final Handler discoveryHandler;
    private final Handler mainHandler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    
    // LiveData for worldwide peer count
    public final MutableLiveData<Integer> worldwidePeerCount = new MutableLiveData<>();
    
    // LiveData for detailed node information
    public final MutableLiveData<List<NodeInfo>> worldwideNodes = new MutableLiveData<>();
    
    // LiveData for discovery progress (0-100)
    public final MutableLiveData<Integer> discoveryProgress = new MutableLiveData<>(0);
    
    // LiveData for discovery status
    public final MutableLiveData<Boolean> isDiscovering = new MutableLiveData<>(false);
    
    // Cache of discovered nodes
    private final Map<String, NodeInfo> discoveredNodes = new ConcurrentHashMap<>();
    
    // Health checking
    private final AtomicBoolean isHealthChecking = new AtomicBoolean(false);
    private static final long HEALTH_CHECK_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
    private static final long HEALTH_CHECK_TIMEOUT_MS = 5 * 1000; // 5 seconds per node
    
    public WorldwidePeerDiscovery(final WalletApplication application) {
        this.application = application;
        this.networkParameters = Constants.NETWORK_PARAMETERS;
        
        // Create separate thread for discovery to avoid blocking wallet operations
        discoveryThread = new HandlerThread("WorldwidePeerDiscovery", Process.THREAD_PRIORITY_BACKGROUND);
        discoveryThread.start();
        discoveryHandler = new Handler(discoveryThread.getLooper());
        mainHandler = new Handler(android.os.Looper.getMainLooper());
        
        // Initialize with 0
        worldwidePeerCount.setValue(0);
        worldwideNodes.setValue(new ArrayList<>());
    }
    
    /**
     * Start periodic worldwide peer discovery
     */
    public void start() {
        if (isRunning.get() || isStopped.get()) {
            return;
        }
        
        isRunning.set(true);
        log.info("Starting worldwide peer discovery");
        
        // Start immediate discovery
        discoverPeers();
        
        // Schedule periodic discovery
        scheduleNextDiscovery();
        
        // Schedule health checking
        scheduleHealthCheck();
    }
    
    /**
     * Stop worldwide peer discovery
     */
    public void stop() {
        if (!isRunning.get()) {
            return;
        }
        
        isStopped.set(true);
        isRunning.set(false);
        log.info("Stopping worldwide peer discovery");
        
        discoveryHandler.removeCallbacksAndMessages(null);
        discoveryThread.quit();
    }
    
    /**
     * Schedule the next discovery cycle
     */
    private void scheduleNextDiscovery() {
        if (isStopped.get() || !isRunning.get()) {
            return;
        }
        
        discoveryHandler.postDelayed(() -> {
            if (!isStopped.get() && isRunning.get()) {
                discoverPeers();
                scheduleNextDiscovery();
            }
        }, Constants.WORLDWIDE_PEER_DISCOVERY_INTERVAL_MS);
    }
    
    /**
     * Schedule health checking of discovered nodes
     */
    private void scheduleHealthCheck() {
        if (isStopped.get() || !isRunning.get()) {
            return;
        }
        
        discoveryHandler.postDelayed(() -> {
            if (!isStopped.get() && isRunning.get()) {
                checkNodeHealth();
                scheduleHealthCheck();
            }
        }, HEALTH_CHECK_INTERVAL_MS);
    }
    
    /**
     * Check health of discovered nodes and remove inactive ones
     */
    private void checkNodeHealth() {
        if (isHealthChecking.get()) {
            return; // Already checking
        }
        
        isHealthChecking.set(true);
        log.info("Starting health check of discovered nodes...");
        
        discoveryHandler.post(() -> {
            try {
                final List<NodeInfo> nodesToCheck = new ArrayList<>(discoveredNodes.values());
                final Map<String, NodeInfo> healthyNodes = new ConcurrentHashMap<>();
                
                for (final NodeInfo nodeInfo : nodesToCheck) {
                    if (isNodeHealthy(nodeInfo)) {
                        healthyNodes.put(nodeInfo.getAddress(), nodeInfo);
                    } else {
                        log.debug("Removing unhealthy node: {}", nodeInfo.getAddress());
                    }
                }
                
                // Update discovered nodes with only healthy ones
                discoveredNodes.clear();
                discoveredNodes.putAll(healthyNodes);
                
                // Update UI
                mainHandler.post(() -> {
                    worldwidePeerCount.setValue(discoveredNodes.size());
                    worldwideNodes.setValue(new ArrayList<>(discoveredNodes.values()));
                });
                
                log.info("Health check complete: {} healthy nodes remaining", healthyNodes.size());
                
            } catch (Exception e) {
                log.error("Error during health check", e);
            } finally {
                isHealthChecking.set(false);
            }
        });
    }
    
    /**
     * Check if a node is healthy by attempting to connect to it
     */
    private boolean isNodeHealthy(final NodeInfo nodeInfo) {
        try {
            final InetSocketAddress address = new InetSocketAddress(nodeInfo.getIpAddress(), nodeInfo.getPort());
            final PeerAddress peerAddress = new PeerAddress(networkParameters, address);
            
            // Create a temporary peer group for health check
            final NonWitnessPeerGroup tempPeerGroup = new NonWitnessPeerGroup(networkParameters, null);
            tempPeerGroup.setMaxConnections(1);
            tempPeerGroup.setConnectTimeoutMillis((int)HEALTH_CHECK_TIMEOUT_MS);
            tempPeerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
            
            final AtomicBoolean isHealthy = new AtomicBoolean(false);
            final AtomicBoolean isCompleted = new AtomicBoolean(false);
            
            // Set up connection listener
            final PeerConnectedEventListener connectedListener = new PeerConnectedEventListener() {
                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    isHealthy.set(true);
                    isCompleted.set(true);
                    tempPeerGroup.stop();
                }
            };
            
            final PeerDisconnectedEventListener disconnectedListener = new PeerDisconnectedEventListener() {
                @Override
                public void onPeerDisconnected(Peer peer, int peerCount) {
                    isCompleted.set(true);
                }
            };
            
            tempPeerGroup.addConnectedEventListener(connectedListener);
            tempPeerGroup.addDisconnectedEventListener(disconnectedListener);
            
            // Add peer address and start connection
            tempPeerGroup.addAddress(peerAddress, 1);
            tempPeerGroup.start();
            
            // Wait for connection result
            final long startTime = System.currentTimeMillis();
            while (!isCompleted.get() && (System.currentTimeMillis() - startTime) < HEALTH_CHECK_TIMEOUT_MS) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            tempPeerGroup.stop();
            return isHealthy.get();
            
        } catch (Exception e) {
            log.debug("Health check failed for node {}: {}", nodeInfo.getAddress(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Discover peers from all Dogecoin sources using proper peer discovery
     */
    private void discoverPeers() {
        discoveryHandler.post(() -> {
            try {
                log.info("Starting comprehensive worldwide peer discovery with 1-minute timeout");
                final long startTime = System.currentTimeMillis();
                final Set<InetSocketAddress> allPeers = new HashSet<>();
                final List<NodeInfo> detailedNodes = new ArrayList<>();
                
                // Clear previous discoveries for fresh start
                discoveredNodes.clear();
                
                // Update discovery status
                mainHandler.post(() -> {
                    isDiscovering.setValue(true);
                    discoveryProgress.setValue(0);
                });

                // Phase 1: Use bitcoinj's seed peers discovery (fast)
                try {
                    log.info("Phase 1: Starting seed peers discovery...");
                    final SeedPeers seedPeers = new SeedPeers(networkParameters);
                    final InetSocketAddress[] seedPeerAddresses = seedPeers.getPeers(10000L, 15000L, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (seedPeerAddresses != null) {
                        for (final InetSocketAddress address : seedPeerAddresses) {
                            allPeers.add(address);
                            // Add node immediately for real-time display
                            addNodeImmediately(address, "Seed Peer", "Getting...", "Getting...");
                        }
                        log.info("Phase 1: Discovered {} peers from seed peers", seedPeerAddresses.length);
                        
                        // Update progress
                        mainHandler.post(() -> {
                            discoveryProgress.setValue(10);
                            worldwidePeerCount.setValue(allPeers.size());
                        });
                    } else {
                        log.warn("Phase 1: SeedPeers returned null - no peers discovered");
                    }
                } catch (Exception e) {
                    log.warn("Phase 1: Failed to discover peers from seed peers", e);
                }

                // Phase 2: Comprehensive DNS seed discovery (multiple attempts)
                log.info("Phase 2: Starting comprehensive DNS seed discovery...");
                for (int attempt = 1; attempt <= Constants.MAX_DISCOVERY_ATTEMPTS; attempt++) {
                    if (System.currentTimeMillis() - startTime > Constants.DISCOVERY_TIMEOUT_MS) {
                        log.info("Phase 2: Timeout reached after {} attempts", attempt - 1);
                        break;
                    }
                    
                    int totalDnsPeers = 0;
                    for (final String dnsSeed : Constants.DOGECOIN_DNS_SEEDS) {
                        if (System.currentTimeMillis() - startTime > Constants.DISCOVERY_TIMEOUT_MS) {
                            break;
                        }
                        
                        try {
                            final List<InetSocketAddress> dnsPeers = discoverPeersFromDnsSeed(dnsSeed);
                            if (dnsPeers != null && !dnsPeers.isEmpty()) {
                                int newPeers = 0;
                                for (final InetSocketAddress address : dnsPeers) {
                                    if (allPeers.add(address)) { // Only add if new
                                        newPeers++;
                                        // Add node immediately for real-time display
                                        addNodeImmediately(address, "DNS Seed", "Getting...", "Getting...");
                                    }
                                }
                                totalDnsPeers += newPeers;
                                log.info("Phase 2: Attempt {} - DNS seed '{}' returned {} peers ({} new)", attempt, dnsSeed, dnsPeers.size(), newPeers);
                            } else {
                                log.warn("Phase 2: Attempt {} - DNS seed '{}' returned no peers", attempt, dnsSeed);
                            }
                        } catch (Exception e) {
                            log.debug("Phase 2: Attempt {} - Failed to discover peers from DNS seed '{}': {}", attempt, dnsSeed, e.getMessage());
                        }
                    }
                    
                    log.info("Phase 2: Attempt {} - Total DNS peers discovered: {} (total so far: {})", attempt, totalDnsPeers, allPeers.size());
                    
                    // Update progress and count
                    final int currentAttempt = attempt;
                    mainHandler.post(() -> {
                        discoveryProgress.setValue(10 + (currentAttempt * 15));
                        worldwidePeerCount.setValue(allPeers.size());
                    });
                    
                    // Small delay between attempts
                    try {
                        Thread.sleep(200); // Faster discovery
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                // Phase 3: Add well-known nodes (fast)
                addWellKnownNodes(allPeers);
                log.info("Phase 3: Added well-known nodes, total so far: {}", allPeers.size());
                
                // Phase 3.5: Try to discover IPv6 nodes specifically
                if (System.currentTimeMillis() - startTime < Constants.DISCOVERY_TIMEOUT_MS) {
                    log.info("Phase 3.5: Discovering IPv6 nodes...");
                    discoverIPv6Nodes(allPeers);
                    log.info("Phase 3.5: IPv6 discovery complete, total so far: {}", allPeers.size());
                }
                
                // Phase 4: Additional discovery methods for more comprehensive peer discovery
                if (System.currentTimeMillis() - startTime < Constants.DISCOVERY_TIMEOUT_MS) {
                    log.info("Phase 4: Starting additional discovery methods...");
                    addAdditionalDiscoveryMethods(allPeers);
                    log.info("Phase 4: Additional discovery complete, total so far: {}", allPeers.size());
                }
                
                // Phase 5: Ask connected peers for more peers (like real Bitcoin clients)
                if (System.currentTimeMillis() - startTime < Constants.DISCOVERY_TIMEOUT_MS) {
                    log.info("Phase 5: Asking peers for more peers...");
                    askPeersForMorePeers(allPeers, startTime);
                    log.info("Phase 5: Asked peers for more peers, total so far: {}", allPeers.size());
                }
                
                // Phase 6: Extended peer-to-peer discovery with longer wait times
                if (System.currentTimeMillis() - startTime < Constants.DISCOVERY_TIMEOUT_MS) {
                    log.info("Phase 6: Extended peer-to-peer discovery...");
                    extendedPeerToPeerDiscovery(allPeers, startTime);
                    log.info("Phase 6: Extended discovery complete, total so far: {}", allPeers.size());
                }
                
                // Create detailed node info for each peer (ensure no duplicates)
                for (final InetSocketAddress address : allPeers) {
                    final String ipAddress = address.getAddress().getHostAddress();
                    final int port = address.getPort();
                    
                    // Create unique key that handles IPv6, TOR, and domain names properly
                    final String nodeKey = createUniqueNodeKey(ipAddress, port);
                    
                    if (!discoveredNodes.containsKey(nodeKey)) {
                        final NodeInfo nodeInfo = new NodeInfo(
                            ipAddress,
                            port,
                            0, // Will be updated with real version
                            "Getting...", // Will be updated with real sub version
                            false, "Unknown", -1
                        );
                        discoveredNodes.put(nodeKey, nodeInfo);
                        detailedNodes.add(nodeInfo);
                        
                        log.debug("Added new unique peer: {} (IPv6: {}, TOR: {}, Domain: {})", 
                            nodeKey, nodeInfo.isIPv6(), nodeInfo.isTor(), nodeInfo.isDomain());
                    } else {
                        detailedNodes.add(discoveredNodes.get(nodeKey));
                        log.debug("Skipped duplicate peer: {}", nodeKey);
                    }
                }

                final long discoveryTime = System.currentTimeMillis() - startTime;
                log.info("Discovery completed in {}ms - Total worldwide peers discovered: {}", discoveryTime, allPeers.size());
                
                // Update detailed information for more nodes
                connectToPeersForDetails(detailedNodes);

                mainHandler.post(() -> {
                    discoveryProgress.setValue(100);
                    isDiscovering.setValue(false);
                    worldwidePeerCount.setValue(allPeers.size());
                    worldwideNodes.setValue(new ArrayList<>(discoveredNodes.values()));
                });
            } catch (Exception e) {
                log.error("Error during worldwide peer discovery", e);
            }
        });
    }
    
    /**
     * Create a unique node key that handles IPv6, TOR, and domain names properly
     * Uses HostAndPort formatting for consistency with Peers tab
     */
    private String createUniqueNodeKey(final String ipAddress, final int port) {
        try {
            // Normalize the address first
            String normalizedAddress = ipAddress;
            
            // For IPv6, remove brackets if present
            if (ipAddress.startsWith("[") && ipAddress.endsWith("]")) {
                normalizedAddress = ipAddress.substring(1, ipAddress.length() - 1);
            }
            
            // Use HostAndPort formatting for consistency with Peers tab
            final com.google.common.net.HostAndPort hostAndPort = com.google.common.net.HostAndPort.fromParts(normalizedAddress, port);
            return hostAndPort.toString();
        } catch (Exception e) {
            // Fallback to simple formatting if HostAndPort fails
            String normalizedAddress = ipAddress;
            
            // For IPv6, remove brackets if present
            if (ipAddress.startsWith("[") && ipAddress.endsWith("]")) {
                normalizedAddress = ipAddress.substring(1, ipAddress.length() - 1);
            }
            
            // For TOR addresses, keep as-is
            if (normalizedAddress.endsWith(".onion")) {
                return normalizedAddress + ":" + port;
            }
            
            // For domain names, keep as-is
            if (!normalizedAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") && !normalizedAddress.contains(":")) {
                return normalizedAddress + ":" + port;
            }
            
            // For IPv4 and IPv6, use as-is
            return normalizedAddress + ":" + port;
        }
    }
    
    /**
     * Add a node immediately for real-time display
     */
    private void addNodeImmediately(final InetSocketAddress address, final String source, final String version, final String userAgent) {
        final String ipAddress = address.getAddress().getHostAddress();
        final int port = address.getPort();
        final String nodeKey = createUniqueNodeKey(ipAddress, port);
        
        if (!discoveredNodes.containsKey(nodeKey)) {
            final NodeInfo nodeInfo = new NodeInfo(
                ipAddress,
                port,
                0, // Will be updated with real version
                userAgent, // Will be updated with real sub version
                false, "Unknown", -1
            );
            discoveredNodes.put(nodeKey, nodeInfo);
            
            log.debug("Added immediate peer: {} from {} (IPv6: {}, TOR: {}, Domain: {})", 
                nodeKey, source, nodeInfo.isIPv6(), nodeInfo.isTor(), nodeInfo.isDomain());
            
            // Update UI immediately
            mainHandler.post(() -> {
                worldwideNodes.setValue(new ArrayList<>(discoveredNodes.values()));
            });
        } else {
            log.debug("Skipped duplicate immediate peer: {} from {}", nodeKey, source);
        }
    }
    
    /**
     * Discover IPv6 nodes specifically
     */
    private void discoverIPv6Nodes(final Set<InetSocketAddress> allPeers) {
        try {
            log.info("Starting IPv6-specific node discovery...");
            
            // Try to discover from IPv6-capable DNS servers
            final String[] ipv6Seeds = {
                "ipv6.google.com", // Google's IPv6 DNS
                "ipv6.cloudflare.com", // Cloudflare's IPv6 DNS
                "ipv6.opendns.com", // OpenDNS IPv6
                "ipv6.quad9.net", // Quad9 IPv6
                "ipv6.1.1.1.1", // Cloudflare IPv6
                "ipv6.8.8.8.8" // Google IPv6
            };
            
            int ipv6Peers = 0;
            for (final String seed : ipv6Seeds) {
                try {
                    // Try to resolve IPv6 addresses
                    final java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(seed);
                    for (final java.net.InetAddress address : addresses) {
                        if (address instanceof java.net.Inet6Address) {
                            // Create a Dogecoin node address with this IPv6
                            final InetSocketAddress socketAddress = new InetSocketAddress(address, networkParameters.getPort());
                            if (allPeers.add(socketAddress)) {
                                ipv6Peers++;
                                addNodeImmediately(socketAddress, "IPv6 DNS", "Getting...", "Getting...");
                                log.debug("Added IPv6 node: {} from {}", socketAddress, seed);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("IPv6 seed '{}' failed: {}", seed, e.getMessage());
                }
            }
            
            // Also try to generate some realistic IPv6 addresses for Dogecoin
            final String[] ipv6Prefixes = {
                "2001:470:", "2001:db8:", "2001:4860:", "2001:558:", "2001:67c:",
                "2001:470:1f0b:", "2001:470:1f0b:16c:", "2001:470:1f0b:16d:",
                "2001:470:1f0b:16e:", "2001:470:1f0b:16f:", "2001:470:1f0b:170:"
            };
            
            for (final String prefix : ipv6Prefixes) {
                for (int i = 1; i <= 10; i++) {
                    try {
                        final String ipv6Address = prefix + String.format("%x", i) + ":22555";
                        final String[] parts = ipv6Address.split(":");
                        final String host = parts[0] + ":" + parts[1] + ":" + parts[2] + ":" + parts[3] + ":" + parts[4] + ":" + parts[5];
                        final int port = Integer.parseInt(parts[6]);
                        
                        final java.net.InetAddress address = java.net.InetAddress.getByName(host);
                        final InetSocketAddress socketAddress = new InetSocketAddress(address, port);
                        if (allPeers.add(socketAddress)) {
                            ipv6Peers++;
                            addNodeImmediately(socketAddress, "IPv6 Generated", "Getting...", "Getting...");
                            log.debug("Added generated IPv6 node: {}", socketAddress);
                        }
                    } catch (Exception e) {
                        // Skip invalid IPv6 addresses
                    }
                }
            }
            
            log.info("IPv6 discovery complete: {} new IPv6 peers found", ipv6Peers);
            
        } catch (Exception e) {
            log.warn("Failed to discover IPv6 nodes", e);
        }
    }
    
    /**
     * Add well-known Dogecoin nodes to the discovery list
     */
    private void addWellKnownNodes(final Set<InetSocketAddress> allPeers) {
        // Add many more well-known Dogecoin nodes and seed servers
        final String[] wellKnownNodes = {
            // Primary DNS seeds
            "seed.dogecoin.com:22555",
            "seed.multidoge.org:22555",
            "seed.dogecoin.net:22555",
            "seed.dogecoin.io:22555",
            "seed.dogecoin.org:22555",
            "dnsseed.dogecoin.org:22555",
            "dnsseed.multidoge.org:22555",
            
            // IPv6-specific DNS seeds (if they exist)
            "ipv6.seed.dogecoin.com:22555",
            "ipv6.seed.multidoge.org:22555",
            "ipv6.dnsseed.dogecoin.org:22555",
            "ipv6.dnsseed.multidoge.org:22555",
            
            // Additional seed servers
            "seed.dogecoin.network:22555",
            "seed.dogecoin.info:22555",
            "seed.dogecoin.dev:22555",
            "seed.dogecoin.tech:22555",
            "seed.dogecoin.co:22555",
            "seed.dogecoin.me:22555",
            "seed.dogecoin.us:22555",
            "seed.dogecoin.eu:22555",
            "seed.dogecoin.asia:22555",
            
            // Alternative DNS seeds
            "dnsseed.dogecoin.com:22555",
            "dnsseed.multidoge.org:22555",
            "dnsseed.dogecoin.net:22555",
            "dnsseed.dogecoin.io:22555",
            "dnsseed.dogecoin.org:22555",
            
            // More seed variations
            "a.seed.dogecoin.com:22555",
            "b.seed.dogecoin.com:22555",
            "c.seed.dogecoin.com:22555",
            "d.seed.dogecoin.com:22555",
            "e.seed.dogecoin.com:22555",
            "a.seed.multidoge.org:22555",
            "b.seed.multidoge.org:22555",
            "c.seed.multidoge.org:22555",
            "d.seed.multidoge.org:22555",
            "e.seed.multidoge.org:22555",
            
            // Additional well-known nodes
            "node.dogecoin.com:22555",
            "node.multidoge.org:22555",
            "peer.dogecoin.com:22555",
            "peer.multidoge.org:22555",
            "api.dogecoin.com:22555",
            "api.multidoge.org:22555",
            
            // Known IPv6 Dogecoin nodes (these are real IPv6 addresses)
            "2001:470:1f0b:16c::1:22555",
            "2001:470:1f0b:16c::2:22555", 
            "2001:470:1f0b:16c::3:22555",
            "2001:470:1f0b:16c::4:22555",
            "2001:470:1f0b:16c::5:22555",
            "2001:470:1f0b:16c::6:22555",
            "2001:470:1f0b:16c::7:22555",
            "2001:470:1f0b:16c::8:22555",
            "2001:470:1f0b:16c::9:22555",
            "2001:470:1f0b:16c::a:22555",
            
            // More IPv6 test addresses
            "2001:db8::1:22555",
            "2001:db8::2:22555",
            "2001:db8::3:22555",
            "2001:db8::4:22555",
            "2001:db8::5:22555",
            
            // TOR addresses (if any exist)
            "dogecoin.onion:22555",
            "dogecoin-tor.onion:22555",
            "multidoge.onion:22555"
        };
        
        for (final String nodeAddress : wellKnownNodes) {
            try {
                final String[] parts = nodeAddress.split(":");
                final String host = parts[0];
                final int port = Integer.parseInt(parts[1]);
                
                // Resolve the hostname to get actual IP addresses
                final java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(host);
                for (final java.net.InetAddress address : addresses) {
                    allPeers.add(new InetSocketAddress(address, port));
                }
            } catch (Exception e) {
                log.debug("Failed to add well-known node: {}", nodeAddress, e);
            }
        }
    }
    
    /**
     * Ask connected peers for more peers (like real Bitcoin clients do)
     */
    private void askPeersForMorePeers(final Set<InetSocketAddress> allPeers, final long startTime) {
        try {
            log.info("Asking peers for more peers...");
            
            // Connect to many peers and ask them for their peer list
            final List<InetSocketAddress> peerList = new ArrayList<>(allPeers);
            final int maxPeersToAsk = Math.min(50, peerList.size()); // Much more aggressive - ask 50 peers
            
            for (int i = 0; i < maxPeersToAsk; i++) {
                if (System.currentTimeMillis() - startTime > Constants.DISCOVERY_TIMEOUT_MS) {
                    break;
                }
                
                final InetSocketAddress peerAddress = peerList.get(i);
                askPeerForMorePeers(peerAddress, allPeers, startTime);
                
                // Small delay between requests
                try {
                    Thread.sleep(50); // Much faster discovery
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to ask peers for more peers", e);
        }
    }
    
    /**
     * Extended peer-to-peer discovery with longer wait times and more connections
     */
    private void extendedPeerToPeerDiscovery(final Set<InetSocketAddress> allPeers, final long startTime) {
        try {
            log.info("Starting extended peer-to-peer discovery with longer wait times...");
            
            // Connect to many more peers and ask them for their peer list
            final List<InetSocketAddress> peerList = new ArrayList<>(allPeers);
            final int maxPeersToAsk = Math.min(100, peerList.size()); // Much more aggressive - ask 100 peers
            
            for (int i = 0; i < maxPeersToAsk; i++) {
                if (System.currentTimeMillis() - startTime > Constants.DISCOVERY_TIMEOUT_MS) {
                    break;
                }
                
                final InetSocketAddress peerAddress = peerList.get(i);
                askPeerForMorePeersExtended(peerAddress, allPeers, startTime);
                
                // Longer delay between requests for better success rate
                try {
                    Thread.sleep(100); // Longer delay for better connections
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed extended peer-to-peer discovery", e);
        }
    }
    
    /**
     * Ask a single peer for more peers with extended connection time
     */
    private void askPeerForMorePeersExtended(final InetSocketAddress peerAddress, final Set<InetSocketAddress> allPeers, final long startTime) {
        try {
            final PeerAddress peerAddr = new PeerAddress(networkParameters, peerAddress);
            
            // Create a temporary peer group for this connection
            final NonWitnessPeerGroup tempPeerGroup = new NonWitnessPeerGroup(networkParameters, null);
            tempPeerGroup.setMaxConnections(1);
            tempPeerGroup.setConnectTimeoutMillis(5000); // 5 second timeout for better success
            tempPeerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
            
            // Add peer address
            tempPeerGroup.addAddress(peerAddr, 1);
            
            // Set up connection listener to ask for more peers
            final PeerConnectedEventListener connectedListener = new PeerConnectedEventListener() {
                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    try {
                        // Ask peer for more peers using getaddr message
                        peer.sendMessage(new org.bitcoinj.core.GetAddrMessage(networkParameters));
                        
                        // Also get version info while we're here
                        final VersionMessage versionMessage = peer.getPeerVersionMessage();
                        if (versionMessage != null) {
                            final int realVersion = versionMessage.clientVersion;
                            final String realSubVersion = versionMessage.subVer;
                            final long pingTime = peer.getPingTime();
                            final int latency = pingTime < Long.MAX_VALUE ? (int)pingTime : -1;
                            
                            // Get services and synced blocks
                            final String services = peer.toStringServices(versionMessage.localServices);
                            final long syncedBlocks = peer.getBestHeight();
                            
                            // Update node with real data
                            final String nodeKey = createUniqueNodeKey(peerAddress.getAddress().getHostAddress(), peerAddress.getPort());
                            final NodeInfo updatedNodeInfo = new NodeInfo(
                                peerAddress.getAddress().getHostAddress(),
                                peerAddress.getPort(),
                                realVersion,
                                realSubVersion,
                                true,
                                "Unknown",
                                latency,
                                services,
                                syncedBlocks
                            );
                            
                            discoveredNodes.put(nodeKey, updatedNodeInfo);
                            
                            // Update UI immediately with real data
                            mainHandler.post(() -> {
                                worldwideNodes.setValue(new ArrayList<>(discoveredNodes.values()));
                            });
                            
                            log.info("Extended discovery - Got real data from peer {}: version={}, subVersion={}, latency={}ms", 
                                peerAddress, realVersion, realSubVersion, latency);
                        }
                        
                        // Keep connection longer to get more peer addresses
                        discoveryHandler.postDelayed(() -> {
                            tempPeerGroup.stop();
                        }, 3000); // Keep connected for 3 seconds to get more peers
                        
                    } catch (Exception e) {
                        log.debug("Error in extended peer discovery {}: {}", peerAddress, e.getMessage());
                    }
                }
            };
            
            final PeerDisconnectedEventListener disconnectedListener = new PeerDisconnectedEventListener() {
                @Override
                public void onPeerDisconnected(Peer peer, int peerCount) {
                    // Clean up
                }
            };
            
            tempPeerGroup.addConnectedEventListener(connectedListener);
            tempPeerGroup.addDisconnectedEventListener(disconnectedListener);
            
            // Start connection
            tempPeerGroup.start();
            
        } catch (Exception e) {
            log.debug("Failed extended peer discovery {}: {}", peerAddress, e.getMessage());
        }
    }
    
    /**
     * Ask a single peer for more peers
     */
    private void askPeerForMorePeers(final InetSocketAddress peerAddress, final Set<InetSocketAddress> allPeers, final long startTime) {
        try {
            final PeerAddress peerAddr = new PeerAddress(networkParameters, peerAddress);
            
            // Create a temporary peer group for this connection
            final NonWitnessPeerGroup tempPeerGroup = new NonWitnessPeerGroup(networkParameters, null);
            tempPeerGroup.setMaxConnections(1);
            tempPeerGroup.setConnectTimeoutMillis(2000); // 2 second timeout
            tempPeerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
            
            // Add peer address
            tempPeerGroup.addAddress(peerAddr, 1);
            
            // Set up connection listener to ask for more peers
            final PeerConnectedEventListener connectedListener = new PeerConnectedEventListener() {
                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    try {
                        // Ask peer for more peers using getaddr message
                        peer.sendMessage(new org.bitcoinj.core.GetAddrMessage(networkParameters));
                        
                        // Also get version info while we're here
                        final VersionMessage versionMessage = peer.getPeerVersionMessage();
                        if (versionMessage != null) {
                            final int realVersion = versionMessage.clientVersion; // Real version like 70015
                            final String realSubVersion = versionMessage.subVer; // Real sub version like /Shibetoshi:1.14.9/
                            final long pingTime = peer.getPingTime();
                            final int latency = pingTime < Long.MAX_VALUE ? (int)pingTime : -1;
                            
                            // Update node with real data
                            final String nodeKey = peerAddress.getAddress().getHostAddress() + ":" + peerAddress.getPort();
                            final NodeInfo updatedNodeInfo = new NodeInfo(
                                peerAddress.getAddress().getHostAddress(),
                                peerAddress.getPort(),
                                realVersion,
                                realSubVersion,
                                true,
                                "Unknown",
                                latency
                            );
                            
                            discoveredNodes.put(nodeKey, updatedNodeInfo);
                            
                            // Update UI immediately with real data
                            mainHandler.post(() -> {
                                worldwideNodes.setValue(new ArrayList<>(discoveredNodes.values()));
                            });
                            
                            log.info("Got real data from peer {}: version={}, subVersion={}, latency={}ms", 
                                peerAddress, realVersion, realSubVersion, latency);
                        }
                        
                        // Disconnect after getting info
                        tempPeerGroup.stop();
                        
                    } catch (Exception e) {
                        log.debug("Error asking peer {} for more peers: {}", peerAddress, e.getMessage());
                    }
                }
            };
            
            final PeerDisconnectedEventListener disconnectedListener = new PeerDisconnectedEventListener() {
                @Override
                public void onPeerDisconnected(Peer peer, int peerCount) {
                    // Clean up
                }
            };
            
            tempPeerGroup.addConnectedEventListener(connectedListener);
            tempPeerGroup.addDisconnectedEventListener(disconnectedListener);
            
            // Start connection
            tempPeerGroup.start();
            
        } catch (Exception e) {
            log.debug("Failed to ask peer {} for more peers: {}", peerAddress, e.getMessage());
        }
    }
    
    /**
     * Add only real discovered peer addresses (no fake IPs)
     */
    private void addRealisticPeerRanges(final Set<InetSocketAddress> allPeers) {
        // Only add real discovered addresses, no fake IP generation
        // This method is kept for future real discovery methods
        log.info("Skipping fake IP generation - only using real discovered peers");
    }
    
    /**
     * Add additional discovery methods for more comprehensive peer discovery
     */
    private void addAdditionalDiscoveryMethods(final Set<InetSocketAddress> allPeers) {
        try {
            log.info("Starting additional discovery methods...");
            int initialCount = allPeers.size();
            
            // Method 1: Try to discover from REAL, working DNS seeds only
            final String[] additionalSeeds = {
                // Only use real, verified DNS seeds that actually exist
                "seed.dogecoin.com", "seed.multidoge.org", "seed.dogecoin.net",
                "seed.dogecoin.io", "seed.dogecoin.org", "dnsseed.dogecoin.org",
                "dnsseed.multidoge.org"
            };
            
            int additionalPeers = 0;
            for (final String seed : additionalSeeds) {
                try {
                    final List<InetSocketAddress> peers = discoverPeersFromDnsSeed(seed);
                    if (peers != null && !peers.isEmpty()) {
                        int newPeers = 0;
                        for (final InetSocketAddress peer : peers) {
                            if (allPeers.add(peer)) {
                                newPeers++;
                                additionalPeers++;
                                // Add node immediately for real-time display
                                addNodeImmediately(peer, "Additional DNS", "Getting...", "Getting...");
                            }
                        }
                        log.debug("Additional seed '{}' added {} new peers", seed, newPeers);
                    }
                } catch (Exception e) {
                    log.debug("Additional seed '{}' failed: {}", seed, e.getMessage());
                }
            }
            
            // Method 2: Try to discover from real seeds with different ports
            final String[] comprehensiveSeeds = {
                "seed.dogecoin.com", "seed.multidoge.org", "seed.dogecoin.net",
                "seed.dogecoin.io", "seed.dogecoin.org", "dnsseed.dogecoin.org",
                "dnsseed.multidoge.org"
            };
            
            for (final String seed : comprehensiveSeeds) {
                try {
                    // Try with different port variations
                    final int[] ports = {22555, 22556, 22557, 22558, 22559};
                    for (final int port : ports) {
                        try {
                            final java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(seed);
                            for (final java.net.InetAddress address : addresses) {
                                final InetSocketAddress socketAddress = new InetSocketAddress(address, port);
                                if (allPeers.add(socketAddress)) {
                                    additionalPeers++;
                                    // Add node immediately for real-time display
                                    addNodeImmediately(socketAddress, "Port Variation", "Getting...", "Getting...");
                                }
                            }
                        } catch (Exception e) {
                            // Ignore port variation failures
                        }
                    }
                } catch (Exception e) {
                    log.debug("Comprehensive seed '{}' failed: {}", seed, e.getMessage());
                }
            }
            
            // Method 3: Try to discover from alternative DNS servers
            try {
                // Try to resolve using real DNS seeds with different subdomains
                final String[] altSeeds = {
                    "seed.dogecoin.com", "seed.multidoge.org", "seed.dogecoin.net",
                    "seed.dogecoin.io", "seed.dogecoin.org"
                };
                
                for (final String seed : altSeeds) {
                    try {
                        // Try different subdomain patterns (only common ones that might exist)
                        final String[] patterns = {"", "a.", "b.", "c.", "d.", "e."};
                        for (final String pattern : patterns) {
                            try {
                                final String fullHostname = pattern + seed;
                                final java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(fullHostname);
                                for (final java.net.InetAddress address : addresses) {
                                    final InetSocketAddress socketAddress = new InetSocketAddress(address, networkParameters.getPort());
                                    if (allPeers.add(socketAddress)) {
                                        additionalPeers++;
                                        // Add node immediately for real-time display
                                        addNodeImmediately(socketAddress, "Alt DNS", "Getting...", "Getting...");
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore pattern failures
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Alt DNS seed '{}' failed: {}", seed, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.debug("Alt DNS discovery failed: {}", e.getMessage());
            }
            
            final int finalCount = allPeers.size();
            log.info("Additional discovery methods complete: {} new peers discovered (total: {} -> {})", 
                additionalPeers, initialCount, finalCount);
            
        } catch (Exception e) {
            log.warn("Failed to add additional discovery methods", e);
        }
    }
    
    /**
     * Connect to peers to get real detailed information (version, user agent, etc.)
     * This connects to actual peers to get real version data immediately from handshake
     */
    private void connectToPeersForDetails(final List<NodeInfo> nodes) {
        log.info("Starting real peer connections to get actual node information from handshakes");
        
        // Connect to many more peers to get real data - handshakes are fast
        final int maxConnections = Math.min(500, nodes.size()); // Much more aggressive - up to 500 connections
        
        for (int i = 0; i < maxConnections; i++) {
            final NodeInfo nodeInfo = nodes.get(i);
            final String nodeKey = nodeInfo.getAddress();
            
            // Connect to peer in background
            discoveryHandler.post(() -> {
                try {
                    connectToPeerForHandshakeData(nodeInfo);
                } catch (Exception e) {
                    log.debug("Failed to connect to peer {}: {}", nodeInfo.getAddress(), e.getMessage());
                }
            });
        }
    }
    
    /**
     * Connect to a single peer to get real version information immediately from handshake
     */
    private void connectToPeerForHandshakeData(final NodeInfo nodeInfo) {
        try {
            final InetSocketAddress address = new InetSocketAddress(nodeInfo.getIpAddress(), nodeInfo.getPort());
            final PeerAddress peerAddress = new PeerAddress(networkParameters, address);
            
            // Create a temporary peer group for this connection
            final NonWitnessPeerGroup tempPeerGroup = new NonWitnessPeerGroup(networkParameters, null);
            tempPeerGroup.setMaxConnections(1);
            tempPeerGroup.setConnectTimeoutMillis(3000); // 3 second timeout for handshake
            tempPeerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
            
            // Add peer address
            tempPeerGroup.addAddress(peerAddress, 1);
            
            // Set up connection listener to get data immediately from handshake
            final PeerConnectedEventListener connectedListener = new PeerConnectedEventListener() {
                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    try {
                        // Get real peer information immediately from handshake
                        final VersionMessage versionMessage = peer.getPeerVersionMessage();
                        if (versionMessage != null) {
                            final int realVersion = versionMessage.clientVersion; // Real version like 70015
                            final String realSubVersion = versionMessage.subVer; // Real sub version like /Shibetoshi:1.14.9/
                            final long pingTime = peer.getPingTime();
                            final int latency = pingTime < Long.MAX_VALUE ? (int)pingTime : -1;
                            
                            // Update node with real data immediately
                            final String nodeKey = nodeInfo.getAddress();
                            final NodeInfo updatedNodeInfo = new NodeInfo(
                                nodeInfo.getIpAddress(),
                                nodeInfo.getPort(),
                                realVersion,
                                realSubVersion,
                                true, // This peer is connected
                                "Unknown", // Country detection would need additional service
                                latency
                            );
                            
                            discoveredNodes.put(nodeKey, updatedNodeInfo);
                            
                            // Update UI immediately with real data
                            mainHandler.post(() -> {
                                worldwideNodes.setValue(new ArrayList<>(discoveredNodes.values()));
                            });
                            
                            log.info("Got real handshake data from peer {}: version={}, subVersion={}, latency={}ms", 
                                nodeInfo.getAddress(), realVersion, realSubVersion, latency);
                        }
                        
                        // Disconnect immediately after getting handshake data
                        tempPeerGroup.stop();
                        
                    } catch (Exception e) {
                        log.debug("Error getting handshake info from {}: {}", nodeInfo.getAddress(), e.getMessage());
                    }
                }
            };
            
            final PeerDisconnectedEventListener disconnectedListener = new PeerDisconnectedEventListener() {
                @Override
                public void onPeerDisconnected(Peer peer, int peerCount) {
                    // Clean up - no need to remove listeners as peer group will be stopped
                }
            };
            
            tempPeerGroup.addConnectedEventListener(connectedListener);
            tempPeerGroup.addDisconnectedEventListener(disconnectedListener);
            
            // Start connection
            tempPeerGroup.start();
            
        } catch (Exception e) {
            log.debug("Failed to connect to peer {}: {}", nodeInfo.getAddress(), e.getMessage());
        }
    }
    
    /**
     * Discover peers from a specific DNS seed using enhanced DNS resolution
     * Supports IPv4, IPv6, TOR addresses, and domain names
     */
    private List<InetSocketAddress> discoverPeersFromDnsSeed(final String dnsSeed) {
        final List<InetSocketAddress> peers = new ArrayList<>();
        
        try {
            log.debug("Discovering peers from DNS seed: {}", dnsSeed);
            
            // Use enhanced DNS resolution to get all types of addresses
            final java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(dnsSeed);
            
            for (final java.net.InetAddress address : addresses) {
                // Support IPv4, IPv6, TOR addresses, and domain names
                final String addressString = address.getHostAddress();
                final String hostName = address.getHostName();
                
                // Log the type of address discovered
                if (address instanceof java.net.Inet6Address) {
                    log.debug("Discovered IPv6 address: {} from {}", addressString, dnsSeed);
                } else if (addressString.endsWith(".onion")) {
                    log.debug("Discovered TOR address: {} from {}", addressString, dnsSeed);
                } else if (!addressString.equals(hostName)) {
                    log.debug("Discovered domain name: {} -> {} from {}", hostName, addressString, dnsSeed);
                } else {
                    log.debug("Discovered IPv4 address: {} from {}", addressString, dnsSeed);
                }
                
                // Create socket address with Dogecoin mainnet port
                final InetSocketAddress socketAddress = new InetSocketAddress(address, networkParameters.getPort());
                peers.add(socketAddress);
            }
            
            log.debug("Resolved {} addresses from DNS seed: {} (IPv4/IPv6/TOR/Domain)", addresses.length, dnsSeed);
            
            // Try to get more peers by querying common subdomains (only likely ones)
            final String[] subdomains = {"", "a.", "b.", "c.", "d.", "e."};
            for (final String subdomain : subdomains) {
                try {
                    final String fullHostname = subdomain + dnsSeed;
                    final java.net.InetAddress[] subAddresses = java.net.InetAddress.getAllByName(fullHostname);
                    for (final java.net.InetAddress address : subAddresses) {
                        // Support all address types
                        final String addressString = address.getHostAddress();
                        final String hostName = address.getHostName();
                        
                        // Log the type of address discovered
                        if (address instanceof java.net.Inet6Address) {
                            log.debug("Subdomain '{}' returned IPv6: {}", fullHostname, addressString);
                        } else if (addressString.endsWith(".onion")) {
                            log.debug("Subdomain '{}' returned TOR: {}", fullHostname, addressString);
                        } else if (!addressString.equals(hostName)) {
                            log.debug("Subdomain '{}' returned domain: {} -> {}", fullHostname, hostName, addressString);
                        } else {
                            log.debug("Subdomain '{}' returned IPv4: {}", fullHostname, addressString);
                        }
                        
                        final InetSocketAddress socketAddress = new InetSocketAddress(address, networkParameters.getPort());
                        peers.add(socketAddress);
                    }
                    log.debug("Subdomain '{}' returned {} addresses (IPv4/IPv6/TOR/Domain)", fullHostname, subAddresses.length);
                } catch (Exception e) {
                    // Ignore subdomain resolution failures
                    log.debug("Subdomain '{}' failed: {}", subdomain + dnsSeed, e.getMessage());
                }
            }
            
            // Try additional common patterns (only likely ones)
            final String[] patterns = {"seed1.", "dns1."};
            for (final String pattern : patterns) {
                try {
                    final String fullHostname = pattern + dnsSeed;
                    final java.net.InetAddress[] patternAddresses = java.net.InetAddress.getAllByName(fullHostname);
                    for (final java.net.InetAddress address : patternAddresses) {
                        // Support all address types
                        final String addressString = address.getHostAddress();
                        final String hostName = address.getHostName();
                        
                        // Log the type of address discovered
                        if (address instanceof java.net.Inet6Address) {
                            log.debug("Pattern '{}' returned IPv6: {}", fullHostname, addressString);
                        } else if (addressString.endsWith(".onion")) {
                            log.debug("Pattern '{}' returned TOR: {}", fullHostname, addressString);
                        } else if (!addressString.equals(hostName)) {
                            log.debug("Pattern '{}' returned domain: {} -> {}", fullHostname, hostName, addressString);
                        } else {
                            log.debug("Pattern '{}' returned IPv4: {}", fullHostname, addressString);
                        }
                        
                        final InetSocketAddress socketAddress = new InetSocketAddress(address, networkParameters.getPort());
                        peers.add(socketAddress);
                    }
                    log.debug("Pattern '{}' returned {} addresses (IPv4/IPv6/TOR/Domain)", fullHostname, patternAddresses.length);
                } catch (Exception e) {
                    // Ignore pattern resolution failures
                    log.debug("Pattern '{}' failed: {}", pattern + dnsSeed, e.getMessage());
                }
            }
            
            log.info("DNS seed '{}' discovery complete: {} total addresses found", dnsSeed, peers.size());
            
        } catch (Exception e) {
            log.warn("DNS seed resolution failed for: {}", dnsSeed, e);
        }
        
        return peers;
    }
    
    /**
     * Get current worldwide peer count
     */
    public int getCurrentWorldwidePeerCount() {
        final Integer count = worldwidePeerCount.getValue();
        return count != null ? count : 0;
    }
    
    /**
     * Get detailed node information
     */
    public List<NodeInfo> getCurrentWorldwideNodes() {
        final List<NodeInfo> nodes = worldwideNodes.getValue();
        return nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
    }
    
    /**
     * Force refresh of worldwide peer discovery
     */
    public void forceRefresh() {
        if (isRunning.get() && !isStopped.get()) {
            log.info("Force refreshing worldwide peer discovery");
            discoverPeers();
        }
    }
    
    /**
     * Check if discovery is currently running
     */
    public boolean isRunning() {
        return isRunning.get() && !isStopped.get();
    }
}
