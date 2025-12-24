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

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

/**
 * Utilities for Hash Time Lock Contract (HTLC) operations
 * 
 * HTLC enables atomic swaps between different blockchains by using:
 * - A secret (random 32 bytes)
 * - A secret hash (SHA256 of the secret)
 * - Time locks for refund mechanisms
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class HtlcUtils {
    private static final Logger log = LoggerFactory.getLogger(HtlcUtils.class);
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Generate a random 32-byte secret for HTLC
     * 
     * @return Secret as hex-encoded string (64 characters)
     */
    public static String generateSecret() {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        return Utils.HEX.encode(secret);
    }
    
    /**
     * Generate SHA256 hash of a secret
     * 
     * @param secret Secret as hex-encoded string
     * @return SHA256 hash as hex-encoded string (64 characters)
     */
    public static String generateSecretHash(String secret) {
        try {
            byte[] secretBytes = Utils.HEX.decode(secret);
            Sha256Hash hash = Sha256Hash.of(secretBytes);
            return hash.toString();
        } catch (Exception e) {
            log.error("Error generating secret hash", e);
            throw new RuntimeException("Failed to generate secret hash", e);
        }
    }
    
    /**
     * Verify that a secret matches a secret hash
     * 
     * @param secret Secret as hex-encoded string
     * @param secretHash Secret hash as hex-encoded string
     * @return true if secret matches hash, false otherwise
     */
    public static boolean verifySecret(String secret, String secretHash) {
        try {
            String computedHash = generateSecretHash(secret);
            return computedHash.equals(secretHash);
        } catch (Exception e) {
            log.error("Error verifying secret", e);
            return false;
        }
    }
    
    /**
     * Generate a secret and its hash
     * 
     * @return Array with [secret, secretHash]
     */
    public static String[] generateSecretAndHash() {
        String secret = generateSecret();
        String secretHash = generateSecretHash(secret);
        return new String[] { secret, secretHash };
    }
    
    /**
     * Validate secret format (must be 64 hex characters)
     * 
     * @param secret Secret to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidSecret(String secret) {
        if (secret == null || secret.length() != 64) {
            return false;
        }
        try {
            Utils.HEX.decode(secret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate secret hash format (must be 64 hex characters)
     * 
     * @param secretHash Secret hash to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidSecretHash(String secretHash) {
        if (secretHash == null || secretHash.length() != 64) {
            return false;
        }
        try {
            Utils.HEX.decode(secretHash);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

