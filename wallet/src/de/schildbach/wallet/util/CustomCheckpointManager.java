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

import android.content.res.AssetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages custom blockchain checkpoints from checkpoints-custom.txt
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class CustomCheckpointManager {
    private static final Logger log = LoggerFactory.getLogger(CustomCheckpointManager.class);
    private static final String CHECKPOINTS_CUSTOM_ASSET = "checkpoints-custom.txt";
    
    public static class Checkpoint {
        public final long timestamp; // Unix timestamp in seconds
        public final int blockHeight;
        public final String blockHash; // Block hash (hex string)
        public final int version; // Block version
        public final String prevBlockHash; // Previous block hash (hex string)
        public final String merkleRoot; // Merkle root (hex string)
        public final long time; // Block timestamp (Unix seconds)
        public final String bits; // Difficulty bits (hex string)
        public final long nonce; // Nonce
        public final String description;
        
        public Checkpoint(long timestamp, int blockHeight, String blockHash, int version,
                String prevBlockHash, String merkleRoot, long time, String bits, long nonce, String description) {
            this.timestamp = timestamp;
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
            this.version = version;
            this.prevBlockHash = prevBlockHash;
            this.merkleRoot = merkleRoot;
            this.time = time;
            this.bits = bits;
            this.nonce = nonce;
            this.description = description;
        }
        
        public String getDisplayName() {
            if (description != null && !description.isEmpty()) {
                return String.format(Locale.getDefault(), "%s (Block %d)", description, blockHeight);
            }
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return String.format(Locale.getDefault(), "%s (Block %d)", 
                    sdf.format(new Date(timestamp * 1000L)), blockHeight);
        }
    }
    
    /**
     * Load custom checkpoints from assets
     */
    public static List<Checkpoint> loadCheckpoints(AssetManager assetManager) {
        List<Checkpoint> checkpoints = new ArrayList<>();
        
        // Add "From Beginning" option (timestamp 0)
        checkpoints.add(new Checkpoint(0, 0, null, 0, null, null, 0, null, 0, "From Beginning"));
        
        try (InputStream is = assetManager.open(CHECKPOINTS_CUSTOM_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                try {
                    // Format: TIMESTAMP=BLOCK_HEIGHT:BLOCK_HASH:VERSION:PREV_BLOCK_HASH:MERKLE_ROOT:TIME:BITS:NONCE:DESCRIPTION
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex == -1) {
                        log.warn("Invalid checkpoint format at line {}: missing '='", lineNumber);
                        continue;
                    }
                    
                    String timestampStr = line.substring(0, equalsIndex).trim();
                    String rest = line.substring(equalsIndex + 1).trim();
                    
                    long timestamp = Long.parseLong(timestampStr);
                    
                    // Split by colon
                    String[] parts = rest.split(":", -1);
                    if (parts.length < 8) {
                        log.warn("Invalid checkpoint format at line {}: expected at least 8 colon-separated values", lineNumber);
                        continue;
                    }
                    
                    int blockHeight = Integer.parseInt(parts[0].trim());
                    String blockHash = parts[1].trim();
                    int version = Integer.parseInt(parts[2].trim());
                    String prevBlockHash = parts[3].trim();
                    String merkleRoot = parts[4].trim();
                    long time = Long.parseLong(parts[5].trim());
                    String bits = parts[6].trim();
                    long nonce = Long.parseLong(parts[7].trim());
                    String description = parts.length > 8 ? parts[8].trim() : "";
                    
                    if (blockHash.isEmpty() || prevBlockHash.isEmpty() || merkleRoot.isEmpty() || bits.isEmpty()) {
                        log.warn("Invalid checkpoint format at line {}: missing required header fields", lineNumber);
                        continue;
                    }
                    
                    checkpoints.add(new Checkpoint(timestamp, blockHeight, blockHash, version,
                            prevBlockHash, merkleRoot, time, bits, nonce, description));
                    log.debug("Loaded checkpoint: timestamp={}, blockHeight={}, blockHash={}, description={}", 
                            timestamp, blockHeight, blockHash, description);
                } catch (NumberFormatException e) {
                    log.warn("Invalid checkpoint format at line {}: {}", lineNumber, e.getMessage());
                }
            }
            
            log.info("Loaded {} custom checkpoints", checkpoints.size() - 1); // -1 for "From Beginning"
        } catch (IOException e) {
            log.warn("Could not load custom checkpoints file: {}", e.getMessage());
        }
        
        return checkpoints;
    }
}

