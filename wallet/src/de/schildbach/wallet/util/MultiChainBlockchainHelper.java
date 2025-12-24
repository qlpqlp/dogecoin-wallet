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

package de.schildbach.wallet.util;

import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.SeedPeers;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for connecting to Bitcoin/Litecoin blockchains directly via P2P nodes
 * 
 * Uses SPV (Simplified Payment Verification) to check headers and transactions
 * without relying on third-party APIs.
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class MultiChainBlockchainHelper {
    private static final Logger log = LoggerFactory.getLogger(MultiChainBlockchainHelper.class);
    
    // Use mainnet for production
    private static final boolean USE_MAINNET = true;
    
    // Bitcoin DNS seeds
    private static final String[] BITCOIN_DNS_SEEDS = {
        "seed.bitcoin.sipa.be",
        "dnsseed.bluematt.me",
        "dnsseed.bitcoin.dashjr.org",
        "seed.bitcoinstats.com",
        "seed.bitcoin.jonasschnelli.ch",
        "seed.btc.petertodd.org"
    };
    
    // Litecoin DNS seeds
    private static final String[] LITECOIN_DNS_SEEDS = {
        "seed-a.litecoin.loshan.co.uk",
        "dnsseed.thrasher.io",
        "dnsseed.litecointools.com",
        "dnsseed.litecoinpool.org"
    };
    
    // Dogecoin DNS seeds
    private static final String[] DOGECOIN_DNS_SEEDS = {
        "seed.multidoge.org",
        "seed2.multidoge.org",
        "seed.dogecoin.com",
        "seed.dogecoin.net",
        "seed.dogecoin.io",
        "dnsseed.dogecoin.org",
        "dnsseed.multidoge.org"
    };
    
    /**
     * Transaction information from blockchain
     */
    public static class TransactionInfo {
        public String txId;
        public int confirmations;
        public boolean confirmed;
        public long blockHeight;
        public String blockHash;
        public long timestamp;
        public boolean exists;
        
        public TransactionInfo(String txId) {
            this.txId = txId;
            this.exists = false;
            this.confirmed = false;
            this.confirmations = 0;
        }
    }
    
    /**
     * Address information from blockchain
     */
    public static class AddressInfo {
        public String address;
        public long balance;
        public int txCount;
        public boolean hasTransactions;
        
        public AddressInfo(String address) {
            this.address = address;
            this.hasTransactions = false;
            this.balance = 0;
            this.txCount = 0;
        }
    }
    
    /**
     * Query transaction status directly from blockchain nodes
     * 
     * @param currency Currency code ("BTC", "LTC", or "DOGE")
     * @param txId Transaction ID
     * @return Transaction information
     */
    public static CompletableFuture<TransactionInfo> queryTransaction(String currency, String txId) {
        CompletableFuture<TransactionInfo> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                NetworkParameters params = MultiChainNetworkHelper.getNetworkParameters(currency);
                TransactionInfo info = new TransactionInfo(txId);
                
                // Create a temporary blockchain and peer group for querying
                BlockStore blockStore = new MemoryBlockStore(params);
                BlockChain blockChain = new BlockChain(params, blockStore);
                
                // Create a minimal wallet for transaction queries
                Wallet wallet = new Wallet(params);
                
                // Create peer group
                PeerGroup peerGroup = new PeerGroup(params, blockChain);
                peerGroup.addWallet(wallet);
                peerGroup.setUserAgent("DogecoinWallet", "1.0");
                peerGroup.setMaxConnections(1);
                peerGroup.setConnectTimeoutMillis(10000);
                
                // Add DNS seed discovery
                try {
                    String[] dnsSeeds;
                    if ("BTC".equals(currency)) {
                        dnsSeeds = BITCOIN_DNS_SEEDS;
                    } else if ("LTC".equals(currency)) {
                        dnsSeeds = LITECOIN_DNS_SEEDS;
                    } else if ("DOGE".equals(currency)) {
                        dnsSeeds = DOGECOIN_DNS_SEEDS;
                    } else {
                        throw new IllegalArgumentException("Unsupported currency: " + currency);
                    }
                    for (String seed : dnsSeeds) {
                        try {
                            // Use SeedPeers for DNS discovery
                            SeedPeers seedPeers = new SeedPeers(params);
                            InetSocketAddress[] peerAddresses = seedPeers.getPeers(5000, 10000, TimeUnit.MILLISECONDS);
                            if (peerAddresses != null) {
                                for (InetSocketAddress peerAddr : peerAddresses) {
                                    peerGroup.addAddress(new PeerAddress(params, peerAddr), 1);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to add DNS seed {}: {}", seed, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to set up DNS discovery: {}", e.getMessage());
                }
                
                // Start peer group
                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(null);
                
                // Wait for connection
                CountDownLatch connectedLatch = new CountDownLatch(1);
                peerGroup.addConnectedEventListener((peer, peerCount) -> {
                    if (peerCount >= 1) {
                        connectedLatch.countDown();
                    }
                });
                
                // Wait up to 30 seconds for connection
                boolean connected = connectedLatch.await(30, TimeUnit.SECONDS);
                
                if (!connected) {
                    log.warn("Failed to connect to {} network within timeout", currency);
                    peerGroup.stopAsync();
                    future.complete(info);
                    return;
                }
                
                // Query transaction from peers
                Sha256Hash txHash = Sha256Hash.wrap(txId);
                
                // Try to get transaction from connected peers
                List<Peer> peers = peerGroup.getConnectedPeers();
                for (Peer peer : peers) {
                    try {
                        // Request transaction
                        GetDataMessage getData = new GetDataMessage(params);
                        getData.addTransaction(txHash, false);
                        peer.sendMessage(getData);
                        
                        // Wait a bit for response
                        Thread.sleep(2000);
                        
                        // Check if transaction was received
                        Set<Transaction> transactions = wallet.getTransactions(false);
                        for (Transaction tx : transactions) {
                            if (tx.getTxId().equals(txHash)) {
                                info.exists = true;
                                
                                // Check if transaction is confirmed
                                int depth = tx.getConfidence().getDepthInBlocks();
                                if (depth > 0) {
                                    info.confirmed = true;
                                    info.confirmations = depth;
                                    
                                    // Get block information
                                    if (tx.getConfidence().getAppearedAtChainHeight() > 0) {
                                        info.blockHeight = tx.getConfidence().getAppearedAtChainHeight();
                                    }
                                    
                                    // Get block hash from appears in hashes
                                    if (tx.getAppearsInHashes() != null && !tx.getAppearsInHashes().isEmpty()) {
                                        Sha256Hash blockHash = tx.getAppearsInHashes().keySet().iterator().next();
                                        info.blockHash = blockHash.toString();
                                    }
                                    
                                    if (tx.getUpdateTime() != null) {
                                        info.timestamp = tx.getUpdateTime().getTime();
                                    }
                                }
                                
                                break;
                            }
                        }
                        
                        if (info.exists) {
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("Error querying transaction from peer {}: {}", peer, e.getMessage());
                    }
                }
                
                // Stop peer group
                peerGroup.stopAsync();
                
                future.complete(info);
                
            } catch (Exception e) {
                log.error("Error querying transaction for {}: {}", currency, txId, e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Query address information directly from blockchain nodes
     * 
     * @param currency Currency code ("BTC", "LTC", or "DOGE")
     * @param address Address to query
     * @return Address information
     */
    public static CompletableFuture<AddressInfo> queryAddress(String currency, String address) {
        CompletableFuture<AddressInfo> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                NetworkParameters params = MultiChainNetworkHelper.getNetworkParameters(currency);
                AddressInfo info = new AddressInfo(address);
                
                // Parse address
                Address addr = Address.fromString(params, address);
                
                // Create a temporary blockchain and peer group for querying
                BlockStore blockStore = new MemoryBlockStore(params);
                BlockChain blockChain = new BlockChain(params, blockStore);
                
                // Create a wallet watching this address
                Wallet wallet = new Wallet(params);
                wallet.addWatchedAddress(addr, 0);
                
                // Create peer group
                PeerGroup peerGroup = new PeerGroup(params, blockChain);
                peerGroup.addWallet(wallet);
                peerGroup.setUserAgent("DogecoinWallet", "1.0");
                peerGroup.setMaxConnections(1);
                peerGroup.setConnectTimeoutMillis(10000);
                peerGroup.setBloomFilteringEnabled(true);
                
                // Add DNS seed discovery
                try {
                    String[] dnsSeeds = "BTC".equals(currency) ? BITCOIN_DNS_SEEDS : LITECOIN_DNS_SEEDS;
                    for (String seed : dnsSeeds) {
                        try {
                            // Use SeedPeers for DNS discovery
                            SeedPeers seedPeers = new SeedPeers(params);
                            InetSocketAddress[] peerAddresses = seedPeers.getPeers(5000, 10000, TimeUnit.MILLISECONDS);
                            if (peerAddresses != null) {
                                for (InetSocketAddress peerAddr : peerAddresses) {
                                    peerGroup.addAddress(new PeerAddress(params, peerAddr), 1);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to add DNS seed {}: {}", seed, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to set up DNS discovery: {}", e.getMessage());
                }
                
                // Start peer group
                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(null);
                
                // Wait for connection
                CountDownLatch connectedLatch = new CountDownLatch(1);
                peerGroup.addConnectedEventListener((peer, peerCount) -> {
                    if (peerCount >= 1) {
                        connectedLatch.countDown();
                    }
                });
                
                // Wait up to 30 seconds for connection
                boolean connected = connectedLatch.await(30, TimeUnit.SECONDS);
                
                if (!connected) {
                    log.warn("Failed to connect to {} network within timeout", currency);
                    peerGroup.stopAsync();
                    future.complete(info);
                    return;
                }
                
                // Wait a bit for wallet to sync
                Thread.sleep(5000);
                
                // Check wallet balance and transactions
                Coin balance = wallet.getBalance();
                if (balance.isPositive()) {
                    info.hasTransactions = true;
                    info.balance = balance.getValue();
                    
                    Set<Transaction> transactions = wallet.getTransactions(false);
                    info.txCount = transactions.size();
                }
                
                // Stop peer group
                peerGroup.stopAsync();
                
                future.complete(info);
                
            } catch (Exception e) {
                log.error("Error querying address for {}: {}", currency, address, e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Get current block height from blockchain nodes
     * 
     * @param currency Currency code ("BTC", "LTC", or "DOGE")
     * @return Current block height
     */
    public static CompletableFuture<Long> getBlockHeight(String currency) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                NetworkParameters params = MultiChainNetworkHelper.getNetworkParameters(currency);
                
                // Create a temporary blockchain and peer group
                BlockStore blockStore = new MemoryBlockStore(params);
                BlockChain blockChain = new BlockChain(params, blockStore);
                
                // Create peer group
                PeerGroup peerGroup = new PeerGroup(params, blockChain);
                peerGroup.setUserAgent("DogecoinWallet", "1.0");
                peerGroup.setMaxConnections(1);
                peerGroup.setConnectTimeoutMillis(10000);
                
                // Add DNS seed discovery
                try {
                    String[] dnsSeeds;
                    if ("BTC".equals(currency)) {
                        dnsSeeds = BITCOIN_DNS_SEEDS;
                    } else if ("LTC".equals(currency)) {
                        dnsSeeds = LITECOIN_DNS_SEEDS;
                    } else if ("DOGE".equals(currency)) {
                        dnsSeeds = DOGECOIN_DNS_SEEDS;
                    } else {
                        throw new IllegalArgumentException("Unsupported currency: " + currency);
                    }
                    for (String seed : dnsSeeds) {
                        try {
                            // Use SeedPeers for DNS discovery
                            SeedPeers seedPeers = new SeedPeers(params);
                            InetSocketAddress[] peerAddresses = seedPeers.getPeers(5000, 10000, TimeUnit.MILLISECONDS);
                            if (peerAddresses != null) {
                                for (InetSocketAddress peerAddr : peerAddresses) {
                                    peerGroup.addAddress(new PeerAddress(params, peerAddr), 1);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to add DNS seed {}: {}", seed, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to set up DNS discovery: {}", e.getMessage());
                }
                
                // Start peer group
                peerGroup.startAsync();
                peerGroup.startBlockChainDownload(null);
                
                // Wait for connection
                CountDownLatch connectedLatch = new CountDownLatch(1);
                peerGroup.addConnectedEventListener((peer, peerCount) -> {
                    if (peerCount >= 1) {
                        connectedLatch.countDown();
                    }
                });
                
                // Wait up to 30 seconds for connection
                boolean connected = connectedLatch.await(30, TimeUnit.SECONDS);
                
                if (!connected) {
                    log.warn("Failed to connect to {} network within timeout", currency);
                    peerGroup.stopAsync();
                    future.complete(0L);
                    return;
                }
                
                // Get block height from chain head
                StoredBlock chainHead = blockChain.getChainHead();
                long height = chainHead.getHeight();
                
                // Stop peer group
                peerGroup.stopAsync();
                
                future.complete(height);
                
            } catch (Exception e) {
                log.error("Error getting block height for {}: {}", currency, e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
}

