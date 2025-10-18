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

import com.google.gson.annotations.SerializedName;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */

import java.net.InetSocketAddress;

/**
 * Represents a Dogecoin peer with all discovered information
 * 
 * @author AI Assistant
 */
public class DogecoinPeer {
    @SerializedName("ip")
    public String ip;
    
    @SerializedName("port")
    public int port;
    
    @SerializedName("version")
    public int version;
    
    @SerializedName("sub_version")
    public String subVersion;
    
    @SerializedName("services")
    public long services;
    
    @SerializedName("synced_blocks")
    public long syncedBlocks;
    
    @SerializedName("latency")
    public long latency;
    
    @SerializedName("status")
    public String status; // "Online", "Discovered", "Offline"
    
    @SerializedName("last_seen")
    public long lastSeen;
    
    @SerializedName("last_handshake_attempt")
    public long lastHandshakeAttempt;
    
    @SerializedName("first_discovered")
    public long firstDiscovered;
    
    @SerializedName("source")
    public String source; // "DNS", "Handshake", "Peer"
    
    @SerializedName("country")
    public String country;
    
    @SerializedName("manually_updated")
    public boolean manuallyUpdated = false;
    
    public DogecoinPeer() {
        this.firstDiscovered = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
        this.status = "Discovered";
    }
    
    public DogecoinPeer(String ip, int port, String source) {
        this();
        this.ip = ip;
        this.port = port;
        this.source = source;
    }
    
    public DogecoinPeer(InetSocketAddress address, String source) {
        this();
        this.ip = address.getAddress().getHostAddress();
        this.port = address.getPort();
        this.source = source;
    }
    
    public String getId() {
        return ip + ":" + port;
    }
    
    public InetSocketAddress getSocketAddress() {
        try {
            return new InetSocketAddress(ip, port);
        } catch (Exception e) {
            return null;
        }
    }
    
    public String getAddress() {
        if (ip.contains(":")) {
            return "[" + ip + "]:" + port;
        }
        return ip + ":" + port;
    }
    
    public String getFormattedLastSeen() {
        long now = System.currentTimeMillis();
        long diff = now - lastSeen;
        
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
        } else {
            return latency + "ms";
        }
    }
    
    
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }
    
    public void updateHandshakeAttempt() {
        this.lastHandshakeAttempt = System.currentTimeMillis();
    }
    
    public void setOnline(int version, String subVersion, long services, long syncedBlocks, long latency) {
        this.status = "Online";
        this.version = version;
        this.subVersion = subVersion;
        this.services = services;
        this.syncedBlocks = syncedBlocks;
        this.latency = latency;
        updateLastSeen();
    }
    
    public void setOffline() {
        this.status = "Offline";
        updateLastSeen();
    }
    
    public void setManuallyUpdated(boolean manuallyUpdated) {
        this.manuallyUpdated = manuallyUpdated;
    }
    
    public boolean isManuallyUpdated() {
        return manuallyUpdated;
    }
    
    public boolean shouldAttemptHandshake() {
        // Only attempt handshake if:
        // 1. Never attempted before (lastHandshakeAttempt == 0)
        // 2. Last attempt was more than 30 seconds ago (much faster for better UX)
        // 3. Status is "Discovered" or "Offline" (but not "Online")
        long now = System.currentTimeMillis();
        long thirtySeconds = 30 * 1000; // 30 seconds in milliseconds
        
        return (lastHandshakeAttempt == 0 || (now - lastHandshakeAttempt) > thirtySeconds) 
               && !"Online".equals(status);
    }
    
    public boolean shouldBeRemoved() {
        // Remove peer if it's been offline for more than 7 days
        // and we've attempted handshake at least once
        if (!"Offline".equals(status) || lastHandshakeAttempt == 0) {
            return false;
        }
        
        long now = System.currentTimeMillis();
        long sevenDays = 7 * 24 * 60 * 60 * 1000; // 7 days in milliseconds
        
        return (now - lastHandshakeAttempt) > sevenDays;
    }
    
    public boolean shouldBeMarkedOffline() {
        // Mark as offline if:
        // 1. Status is "Discovered" (never successfully handshaked)
        // 2. AND it's been more than 48 hours since first discovered
        // OR
        // 3. Status is not "Online" and it's been more than 48 hours since last handshake attempt
        
        long now = System.currentTimeMillis();
        long fortyEightHours = 48 * 60 * 60 * 1000; // 48 hours in milliseconds
        
        // Don't mark peers as offline if they were just loaded (within last 5 minutes)
        long fiveMinutes = 5 * 60 * 1000;
        if ((now - lastSeen) < fiveMinutes) {
            return false;
        }
        
        if ("Discovered".equals(status)) {
            // For discovered peers, check if it's been 48 hours since first discovery
            return (now - firstDiscovered) > fortyEightHours;
        } else if (!"Online".equals(status) && lastHandshakeAttempt > 0) {
            // For other non-online peers, check if it's been 48 hours since last attempt
            return (now - lastHandshakeAttempt) > fortyEightHours;
        }
        
        return false;
    }
    
    /**
     * Convert services number to readable service names
     */
    public String getServicesText() {
        if (services == 0) {
            return "Unknown";
        }
        
        StringBuilder serviceNames = new StringBuilder();
        
        // Bitcoin/Dogecoin service flags
        if ((services & 1L) != 0) {
            serviceNames.append("NETWORK");
        }
        if ((services & 2L) != 0) {
            if (serviceNames.length() > 0) serviceNames.append(", ");
            serviceNames.append("GETUTXO");
        }
        if ((services & 4L) != 0) {
            if (serviceNames.length() > 0) serviceNames.append(", ");
            serviceNames.append("BLOOM");
        }
        if ((services & 8L) != 0) {
            if (serviceNames.length() > 0) serviceNames.append(", ");
            serviceNames.append("WITNESS");
        }
        if ((services & 16L) != 0) {
            if (serviceNames.length() > 0) serviceNames.append(", ");
            serviceNames.append("COMPACT_FILTERS");
        }
        if ((services & 32L) != 0) {
            if (serviceNames.length() > 0) serviceNames.append(", ");
            serviceNames.append("NETWORK_LIMITED");
        }
        
        // If no known services, show the raw number
        if (serviceNames.length() == 0) {
            return String.valueOf(services);
        }
        
        return serviceNames.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DogecoinPeer peer = (DogecoinPeer) obj;
        return port == peer.port && ip.equals(peer.ip);
    }
    
    @Override
    public int hashCode() {
        return ip.hashCode() * 31 + port;
    }
    
    @Override
    public String toString() {
        return "DogecoinPeer{" +
                "address='" + getAddress() + '\'' +
                ", status='" + status + '\'' +
                ", version=" + version +
                ", subVersion='" + subVersion + '\'' +
                '}';
    }
}
