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

package de.schildbach.wallet.data;

import java.net.InetSocketAddress;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import java.util.Date;

/**
 * Detailed information about a discovered Dogecoin node
 * 
 * @author AI Assistant
 */
public class NodeInfo {
    private final String ipAddress;
    private final int port;
    private final int version; // Real version number like 70015
    private final String subVersion; // Sub version like /Shibetoshi:1.14.9/
    private final long lastSeen;
    private final boolean isConnected;
    private final String country;
    private final int latency;
    private final String services; // Services like "NETWORK & BLOOM"
    private final long syncedBlocks; // Number of synced blocks
    
    public NodeInfo(final String ipAddress, final int port, final int version, 
                   final String subVersion, final boolean isConnected) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.version = version;
        this.subVersion = subVersion != null ? subVersion : "Unknown";
        this.lastSeen = System.currentTimeMillis();
        this.isConnected = isConnected;
        this.country = "Unknown";
        this.latency = -1;
        this.services = "Unknown";
        this.syncedBlocks = -1;
    }
    
    public NodeInfo(final String ipAddress, final int port, final int version, 
                   final String subVersion, final boolean isConnected,
                   final String country, final int latency) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.version = version;
        this.subVersion = subVersion != null ? subVersion : "Unknown";
        this.lastSeen = System.currentTimeMillis();
        this.isConnected = isConnected;
        this.country = country != null ? country : "Unknown";
        this.latency = latency;
        this.services = "Unknown";
        this.syncedBlocks = -1;
    }
    
    public NodeInfo(final String ipAddress, final int port, final int version, 
                   final String subVersion, final boolean isConnected,
                   final String country, final int latency, final String services, final long syncedBlocks) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.version = version;
        this.subVersion = subVersion != null ? subVersion : "Unknown";
        this.lastSeen = System.currentTimeMillis();
        this.isConnected = isConnected;
        this.country = country != null ? country : "Unknown";
        this.latency = latency;
        this.services = services != null ? services : "Unknown";
        this.syncedBlocks = syncedBlocks;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public int getPort() {
        return port;
    }
    
    public int getVersion() {
        return version;
    }
    
    public String getSubVersion() {
        return subVersion;
    }
    
    public String getUserAgent() {
        return subVersion; // For backward compatibility
    }
    
    public long getLastSeen() {
        return lastSeen;
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public String getCountry() {
        return country;
    }
    
    public int getLatency() {
        return latency;
    }
    
    public String getServices() {
        return services;
    }
    
    public long getSyncedBlocks() {
        return syncedBlocks;
    }
    
    public String getAddress() {
        // Use HostAndPort formatting like the Peers tab for consistency
        try {
            final com.google.common.net.HostAndPort hostAndPort = com.google.common.net.HostAndPort.fromParts(ipAddress, port);
            return hostAndPort.toString();
        } catch (Exception e) {
            // Fallback to simple formatting if HostAndPort fails
            if (ipAddress.contains(":")) {
                return "[" + ipAddress + "]:" + port;
            }
            return ipAddress + ":" + port;
        }
    }
    
    public String getDisplayAddress() {
        // For display purposes, show the address with proper formatting
        return getAddress();
    }
    
    public boolean isIPv6() {
        return ipAddress.contains(":");
    }
    
    public boolean isTor() {
        return ipAddress.endsWith(".onion");
    }
    
    public boolean isDomain() {
        // Check if it's a domain name (not an IP address)
        return !ipAddress.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") && !ipAddress.contains(":");
    }
    
    public String getFormattedLastSeen() {
        final long now = System.currentTimeMillis();
        final long diff = now - lastSeen;
        
        if (diff < 60000) { // Less than 1 minute
            return "Just now";
        } else if (diff < 3600000) { // Less than 1 hour
            return (diff / 60000) + "m ago";
        } else if (diff < 86400000) { // Less than 1 day
            return (diff / 3600000) + "h ago";
        } else {
            return (diff / 86400000) + "d ago";
        }
    }
    
    public String getLatencyText() {
        if (latency < 0) {
            return "Unknown";
        } else if (latency < 100) {
            return latency + "ms (Excellent)";
        } else if (latency < 300) {
            return latency + "ms (Good)";
        } else if (latency < 1000) {
            return latency + "ms (Fair)";
        } else {
            return latency + "ms (Poor)";
        }
    }
    
    @Override
    public String toString() {
        return "NodeInfo{" +
                "address='" + getAddress() + '\'' +
                ", version=" + version +
                ", subVersion='" + subVersion + '\'' +
                ", connected=" + isConnected +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) obj;
        return port == nodeInfo.port && ipAddress.equals(nodeInfo.ipAddress);
    }
    
    @Override
    public int hashCode() {
        return ipAddress.hashCode() * 31 + port;
    }
}
