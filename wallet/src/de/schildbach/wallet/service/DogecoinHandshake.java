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
import java.io.IOException;
import de.schildbach.wallet.data.DogecoinPeer;
import de.schildbach.wallet.Constants;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles Dogecoin protocol handshakes using direct socket approach
 * 
 * @author AI Assistant
 */
public class DogecoinHandshake {
    private static final String TAG = "DogecoinHandshake";
    private static final int HANDSHAKE_TIMEOUT_MS = 10000; // 10 seconds
    
    private final ExecutorService executor;
    private final PeerStorageManager storageManager;
    private final HandshakeCallback callback;
    
    public interface HandshakeCallback {
        void onHandshakeComplete(DogecoinPeer peer, List<InetSocketAddress> newPeers);
        void onHandshakeFailed(DogecoinPeer peer, String reason);
    }
    
    public DogecoinHandshake(PeerStorageManager storageManager, HandshakeCallback callback) {
        this.storageManager = storageManager;
        this.callback = callback;
        this.executor = Executors.newFixedThreadPool(2); // Reduce concurrent handshakes
    }
    
    /**
     * Perform handshake with a peer using direct socket approach
     */
    public void performHandshake(DogecoinPeer peer) {
        Log.i(TAG, "performHandshake called for " + peer.getAddress());
        if (executor.isShutdown()) {
            Log.w(TAG, "Executor is shutdown, cannot perform handshake for " + peer.getAddress());
            if (callback != null) {
                callback.onHandshakeFailed(peer, "Service is shutting down");
            }
            return;
        }
        executor.execute(() -> {
            try {
                Log.i(TAG, "Starting handshake with " + peer.getAddress());
                // Reset parsed values for this handshake
                peerVersion = 0;
                peerSubVersion = "Unknown";
                peerServices = 0;
                peerSyncedBlocks = 0;
                
                InetSocketAddress address = peer.getSocketAddress();
                if (address == null) {
                    Log.w(TAG, "Invalid address for peer: " + peer.getAddress());
                    if (callback != null) {
                        callback.onHandshakeFailed(peer, "Invalid address");
                    }
                    return;
                }
                
                Log.i(TAG, "Calling captureAddrMessageDirectly for " + peer.getAddress());
                List<InetSocketAddress> newPeers = captureAddrMessageDirectly(peer);
                
                // Update peer with successful handshake using parsed information
                Log.i(TAG, "Setting peer online with: version=" + peerVersion + ", subVersion=" + peerSubVersion + 
                          ", services=" + peerServices + ", blocks=" + peerSyncedBlocks);
                peer.setOnline(peerVersion, peerSubVersion, peerServices, peerSyncedBlocks, 0L);
                Log.i(TAG, "Handshake completed with " + peer.getAddress() + " - found " + newPeers.size() + " new peers");
                
                if (callback != null) {
                    callback.onHandshakeComplete(peer, newPeers);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Handshake failed with " + peer.getAddress() + ": " + e.getMessage(), e);
                if (callback != null) {
                    callback.onHandshakeFailed(peer, e.getMessage());
                }
            }
        });
    }
    
    /**
     * Direct socket handshake approach
     * Many Bitcoin/Dogecoin peers don't follow protocol strictly, so we focus on version exchange
     */
    private List<InetSocketAddress> captureAddrMessageDirectly(DogecoinPeer peer) {
        List<InetSocketAddress> newPeers = new ArrayList<>();
        
        try {
            InetSocketAddress address = peer.getSocketAddress();
            if (address == null) {
                Log.w(TAG, "No address for peer: " + peer.getAddress());
                return newPeers;
            }
            
            Log.d(TAG, "Starting direct handshake with " + address);
            
            // Create direct socket connection
            java.net.Socket socket = new java.net.Socket();
            socket.setSoTimeout(10000); // 10 second timeout
            socket.connect(address, 5000); // 5 second connection timeout
            
            Log.d(TAG, "Connected to " + address);
            
            java.io.DataInputStream in = new java.io.DataInputStream(socket.getInputStream());
            java.io.DataOutputStream out = new java.io.DataOutputStream(socket.getOutputStream());
            
            // Send version message
            Log.d(TAG, "Sending version message to " + address);
            sendVersionMessageDirect(out, address);
            
            // Wait for version response (direct approach - don't require verack)
            if (waitForVersionResponseRelaxed(in)) {
                Log.d(TAG, "Version response received, sending verack");
                // Send our verack
                sendVerackDirect(out);
                
                // Try to send getaddr and wait for addr response (optional)
                try {
                    Log.d(TAG, "Sending getaddr to " + address);
                    sendGetAddrDirect(out);
                    newPeers = waitForAddrResponseRelaxed(in);
                    Log.d(TAG, "Received " + newPeers.size() + " new peers from " + address);
                } catch (Exception e) {
                    Log.d(TAG, "getaddr failed (normal): " + e.getMessage());
                    // This is normal - many peers don't respond to getaddr
                }
            } else {
                Log.w(TAG, "No version response from " + address);
            }
            
            socket.close();
            
        } catch (Exception e) {
            Log.w(TAG, "Error in handshake with " + peer.getAddress() + ": " + e.getMessage());
        }
        
        return newPeers;
    }
    
    /**
     * Send version message directly to peer
     */
    private void sendVersionMessageDirect(java.io.DataOutputStream out, InetSocketAddress address) throws IOException {
        // Create version message payload
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream payloadOut = new java.io.DataOutputStream(baos);
        
        // Version (4 bytes) - little-endian
        payloadOut.writeInt(Integer.reverseBytes(70015)); // Dogecoin protocol version
        
        // Services (8 bytes) - little-endian
        payloadOut.writeLong(Long.reverseBytes(1L)); // NODE_NETWORK
        
        // Timestamp (8 bytes) - little-endian
        payloadOut.writeLong(Long.reverseBytes(System.currentTimeMillis() / 1000));
        
        // Address of receiving node (26 bytes) - little-endian
        payloadOut.writeLong(Long.reverseBytes(1L)); // services
        byte[] ipBytes = address.getAddress().getAddress();
        if (ipBytes.length == 4) {
            // IPv4 mapped to IPv6
            payloadOut.write(new byte[12]); // 12 zero bytes
            payloadOut.write(ipBytes); // 4 IPv4 bytes
        } else {
            payloadOut.write(ipBytes); // 16 IPv6 bytes
        }
        payloadOut.writeShort(Integer.reverseBytes(address.getPort())); // big-endian port
        
        // Address of sending node (26 bytes) - same as receiving
        payloadOut.writeLong(Long.reverseBytes(1L)); // services
        if (ipBytes.length == 4) {
            payloadOut.write(new byte[12]); // 12 zero bytes
            payloadOut.write(ipBytes); // 4 IPv4 bytes
        } else {
            payloadOut.write(ipBytes); // 16 IPv6 bytes
        }
        payloadOut.writeShort(Integer.reverseBytes(address.getPort())); // big-endian port
        
        // Nonce (8 bytes) - little-endian
        payloadOut.writeLong(Long.reverseBytes(System.currentTimeMillis()));
        
        // User agent (variable length)
        String userAgent = Constants.USER_AGENT;
        byte[] userAgentBytes = userAgent.getBytes("UTF-8");
        payloadOut.writeByte(userAgentBytes.length);
        payloadOut.write(userAgentBytes);
        
        // Start height (4 bytes) - little-endian
        payloadOut.writeInt(Integer.reverseBytes(0));
        
        // Relay (1 byte)
        payloadOut.writeByte(1);
        
        byte[] payload = baos.toByteArray();
        
        // Send message header (following Bitcoin protocol format)
        out.writeInt((int) Constants.NETWORK_PARAMETERS.getPacketMagic()); // Dogecoin magic
        out.write("version".getBytes());
        out.write(new byte[5]); // Pad to 12 bytes
        out.writeInt(payload.length);
        out.writeInt(calculateChecksum(payload)); // Proper checksum calculation
        
        // Send payload
        out.write(payload);
        out.flush();
    }
    
    /**
     * Send verack message directly to peer
     */
    private void sendVerackDirect(java.io.DataOutputStream out) throws IOException {
        out.writeInt((int) Constants.NETWORK_PARAMETERS.getPacketMagic()); // Dogecoin magic
        out.write("verack".getBytes());
        out.write(new byte[6]); // Pad to 12 bytes
        out.writeInt(0); // No payload
        out.writeInt(0); // Checksum (no payload)
        out.flush();
    }
    
    /**
     * Send getaddr message directly to peer
     */
    private void sendGetAddrDirect(java.io.DataOutputStream out) throws IOException {
        out.writeInt((int) Constants.NETWORK_PARAMETERS.getPacketMagic()); // Dogecoin magic
        out.write("getaddr".getBytes());
        out.write(new byte[5]); // Pad to 12 bytes
        out.writeInt(0); // No payload
        out.writeInt(0); // Checksum (no payload)
        out.flush();
    }
    
    /**
     * Wait for version response and parse peer's version information
     */
    private boolean waitForVersionResponseRelaxed(java.io.DataInputStream in) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 second timeout
        
        Log.d(TAG, "Waiting for version response...");
        
        while (System.currentTimeMillis() - startTime < timeout) {
            if (in.available() > 0) {
                Log.d(TAG, "Data available, reading message...");
                // Read magic bytes
                int magic = in.readInt();
                Log.d(TAG, "Read magic: 0x" + Integer.toHexString(magic));
                if (magic == 0xc0c0c0c0) { // Dogecoin magic
                    // Read command
                    byte[] command = new byte[12];
                    in.readFully(command);
                    String commandStr = new String(command).trim();
                    Log.d(TAG, "Read command: '" + commandStr + "'");
                    
                    if ("version".equals(commandStr)) {
                        Log.d(TAG, "Version message received, parsing...");
                        // Parse version message payload to get peer's real information
                        int payloadLength = in.readInt();
                        in.readInt(); // checksum
                        Log.d(TAG, "Payload length: " + payloadLength);
                        if (payloadLength > 0) {
                            byte[] payload = new byte[payloadLength];
                            in.readFully(payload);
                            parseVersionMessage(payload);
                        }
                        return true;
                    }
                } else {
                    Log.d(TAG, "Wrong magic bytes, skipping message");
                }
            }
            Thread.sleep(100);
        }
        
        Log.d(TAG, "Timeout waiting for version response");
        return false;
    }
    
    // Store parsed version information
    private int peerVersion = 0;
    private String peerSubVersion = "Unknown";
    private long peerServices = 0;
    private long peerSyncedBlocks = 0;
    
    /**
     * Parse version message payload to extract peer's information
     */
    private void parseVersionMessage(byte[] payload) {
        try {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(payload);
            java.io.DataInputStream dis = new java.io.DataInputStream(bis);
            
            // Read version (4 bytes, little-endian)
            peerVersion = Integer.reverseBytes(dis.readInt());
            
            // Read services (8 bytes, little-endian)
            peerServices = Long.reverseBytes(dis.readLong());
            
            // Read timestamp (8 bytes, little-endian)
            long timestamp = Long.reverseBytes(dis.readLong());
            
            // Read receiver address (26 bytes) - skip
            dis.skipBytes(26);
            
            // Read sender address (26 bytes) - skip  
            dis.skipBytes(26);
            
            // Read nonce (8 bytes) - skip
            dis.skipBytes(8);
            
            // Read user agent length (1 byte)
            int userAgentLength = dis.readUnsignedByte();
            
            // Read user agent (sub-version)
            if (userAgentLength > 0) {
                byte[] userAgentBytes = new byte[userAgentLength];
                dis.readFully(userAgentBytes);
                peerSubVersion = new String(userAgentBytes, "UTF-8");
            }
            
            // Read start height (4 bytes, little-endian)
            peerSyncedBlocks = Integer.reverseBytes(dis.readInt());
            
            Log.d(TAG, "Parsed peer version: " + peerVersion + ", sub-version: " + peerSubVersion + 
                      ", services: " + peerServices + ", blocks: " + peerSyncedBlocks);
                      
        } catch (Exception e) {
            Log.w(TAG, "Error parsing version message: " + e.getMessage());
            // Keep default values
        }
    }
    
    /**
     * Wait for addr response with shorter timeout
     */
    private List<InetSocketAddress> waitForAddrResponseRelaxed(java.io.DataInputStream in) throws IOException, InterruptedException {
        List<InetSocketAddress> newPeers = new ArrayList<>();
        
        try {
            long startTime = System.currentTimeMillis();
            long timeout = 3000; // 3 second timeout
            
            while (System.currentTimeMillis() - startTime < timeout) {
                if (in.available() > 0) {
                    // Read magic bytes
                    int magic = in.readInt();
                    if (magic == 0xc0c0c0c0) { // Dogecoin magic
                        // Read command
                        byte[] command = new byte[12];
                        in.readFully(command);
                        String commandStr = new String(command).trim();
                        
                        if ("addr".equals(commandStr)) {
                            // Read payload length
                            int payloadLength = in.readInt();
                            // Read checksum
                            in.readInt();
                            
                            // Parse the addr message payload
                            if (payloadLength > 0) {
                                byte[] payload = new byte[payloadLength];
                                in.readFully(payload);
                                newPeers = parseAddrMessagePayload(payload);
                            }
                            
                            break; // Exit loop after receiving addr message
                        }
                    }
                }
                Thread.sleep(50);
            }
            
        } catch (Exception e) {
            // This is normal - many peers don't respond to getaddr
        }
        
        return newPeers;
    }
    
    /**
     * Parse addr message payload to extract peer addresses
     */
    private List<InetSocketAddress> parseAddrMessagePayload(byte[] payload) {
        List<InetSocketAddress> peers = new ArrayList<>();
        
        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(payload);
            java.io.DataInputStream dis = new java.io.DataInputStream(bais);
            
            // Read count (variable-length integer)
            long count = readVarInt(dis);
            
            for (long i = 0; i < count && i < 1000; i++) { // Limit to 1000 addresses for safety
                try {
                    // Read timestamp (4 bytes, little-endian)
                    int timestamp = Integer.reverseBytes(dis.readInt());
                    
                    // Read services (8 bytes, little-endian)
                    long services = Long.reverseBytes(dis.readLong());
                    
                    // Read IP address (16 bytes)
                    byte[] ipBytes = new byte[16];
                    dis.readFully(ipBytes);
                    
                    // Read port (2 bytes, big-endian)
                    int port = dis.readUnsignedShort();
                    
                    // Convert IP address (handles IPv4 mapped to IPv6)
                    java.net.InetAddress ipAddress = java.net.InetAddress.getByAddress(ipBytes);
                    InetSocketAddress address = new InetSocketAddress(ipAddress, port);
                    peers.add(address);
                } catch (Exception e) {
                    // Skip invalid addresses
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error parsing addr message payload: " + e.getMessage());
        }
        
        return peers;
    }
    
    /**
     * Read variable-length integer from stream
     */
    private long readVarInt(java.io.DataInputStream dis) throws IOException {
        int firstByte = dis.readUnsignedByte();
        if (firstByte < 0xfd) {
            return firstByte;
        } else if (firstByte == 0xfd) {
            return dis.readUnsignedShort();
        } else if (firstByte == 0xfe) {
            return dis.readInt() & 0xFFFFFFFFL;
        } else {
            return dis.readLong();
        }
    }
    
    /**
     * Calculate double SHA256 checksum for message payload (Bitcoin protocol)
     */
    private int calculateChecksum(byte[] payload) {
        try {
            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            byte[] firstHash = sha256.digest(payload);
            byte[] secondHash = sha256.digest(firstHash);
            
            // Return first 4 bytes as little-endian integer
            return (secondHash[0] & 0xFF) | 
                   ((secondHash[1] & 0xFF) << 8) | 
                   ((secondHash[2] & 0xFF) << 16) | 
                   ((secondHash[3] & 0xFF) << 24);
        } catch (Exception e) {
            Log.w(TAG, "Error calculating checksum: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Shutdown the executor
     */
    public void shutdown() {
        executor.shutdown();
    }
}