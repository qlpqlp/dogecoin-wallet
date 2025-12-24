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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Builder for Hash Time Lock Contract (HTLC) scripts
 * 
 * HTLC scripts enable atomic swaps by locking coins until:
 * 1. Secret is revealed (claim path)
 * 2. Time expires (refund path)
 * 
 * Script structure:
 * OP_IF
 *   OP_SHA256 <secretHash> OP_EQUALVERIFY
 *   OP_DUP OP_HASH160 <recipientPubKeyHash> OP_EQUALVERIFY OP_CHECKSIG
 * OP_ELSE
 *   <locktime> OP_CHECKLOCKTIMEVERIFY OP_DROP
 *   OP_DUP OP_HASH160 <refundPubKeyHash> OP_EQUALVERIFY OP_CHECKSIG
 * OP_ENDIF
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class HtlcScriptBuilder {
    private static final Logger log = LoggerFactory.getLogger(HtlcScriptBuilder.class);
    
    /**
     * Create an HTLC script for Dogecoin
     * 
     * @param secretHash SHA256 hash of the secret (32 bytes, hex encoded)
     * @param recipientPubKeyHash Recipient's public key hash (20 bytes)
     * @param refundPubKeyHash Refunder's public key hash (20 bytes)
     * @param locktime Block height or timestamp for refund (as long)
     * @return HTLC script
     */
    public static Script createHtlcScript(String secretHash, byte[] recipientPubKeyHash, 
                                         byte[] refundPubKeyHash, long locktime) {
        try {
            // Decode secret hash from hex
            byte[] secretHashBytes = Utils.HEX.decode(secretHash);
            if (secretHashBytes.length != 32) {
                throw new IllegalArgumentException("Secret hash must be 32 bytes");
            }
            
            // Build HTLC script
            ScriptBuilder builder = new ScriptBuilder();
            
            // OP_IF - Claim path (secret revealed)
            builder.op(ScriptOpCodes.OP_IF);
            
            // OP_SHA256 <secretHash> OP_EQUALVERIFY
            builder.op(ScriptOpCodes.OP_SHA256);
            builder.data(secretHashBytes);
            builder.op(ScriptOpCodes.OP_EQUALVERIFY);
            
            // OP_DUP OP_HASH160 <recipientPubKeyHash> OP_EQUALVERIFY OP_CHECKSIG
            builder.op(ScriptOpCodes.OP_DUP);
            builder.op(ScriptOpCodes.OP_HASH160);
            builder.data(recipientPubKeyHash);
            builder.op(ScriptOpCodes.OP_EQUALVERIFY);
            builder.op(ScriptOpCodes.OP_CHECKSIG);
            
            // OP_ELSE - Refund path (time expired)
            builder.op(ScriptOpCodes.OP_ELSE);
            
            // <locktime> OP_CHECKLOCKTIMEVERIFY OP_DROP
            builder.number(locktime);
            builder.op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY);
            builder.op(ScriptOpCodes.OP_DROP);
            
            // OP_DUP OP_HASH160 <refundPubKeyHash> OP_EQUALVERIFY OP_CHECKSIG
            builder.op(ScriptOpCodes.OP_DUP);
            builder.op(ScriptOpCodes.OP_HASH160);
            builder.data(refundPubKeyHash);
            builder.op(ScriptOpCodes.OP_EQUALVERIFY);
            builder.op(ScriptOpCodes.OP_CHECKSIG);
            
            // OP_ENDIF
            builder.op(ScriptOpCodes.OP_ENDIF);
            
            Script script = builder.build();
            log.debug("Created HTLC script: {}", script);
            
            return script;
        } catch (Exception e) {
            log.error("Error creating HTLC script", e);
            throw new RuntimeException("Failed to create HTLC script", e);
        }
    }
    
    /**
     * Create an HTLC script using Address objects
     * 
     * @param secretHash SHA256 hash of the secret (hex encoded)
     * @param recipientAddress Recipient's address
     * @param refundAddress Refunder's address
     * @param locktime Block height or timestamp for refund
     * @return HTLC script
     */
    public static Script createHtlcScript(String secretHash, Address recipientAddress, 
                                         Address refundAddress, long locktime) {
        byte[] recipientPubKeyHash = recipientAddress.getHash();
        byte[] refundPubKeyHash = refundAddress.getHash();
        
        return createHtlcScript(secretHash, recipientPubKeyHash, refundPubKeyHash, locktime);
    }
    
    /**
     * Create an HTLC script using ECKey objects
     * 
     * @param secretHash SHA256 hash of the secret (hex encoded)
     * @param recipientKey Recipient's ECKey
     * @param refundKey Refunder's ECKey
     * @param locktime Block height or timestamp for refund
     * @return HTLC script
     */
    public static Script createHtlcScript(String secretHash, ECKey recipientKey, 
                                         ECKey refundKey, long locktime) {
        byte[] recipientPubKeyHash = recipientKey.getPubKeyHash();
        byte[] refundPubKeyHash = refundKey.getPubKeyHash();
        
        return createHtlcScript(secretHash, recipientPubKeyHash, refundPubKeyHash, locktime);
    }
    
    /**
     * Create a claim script (spend from HTLC using secret)
     * 
     * @param secret The secret (32 bytes, hex encoded)
     * @param recipientSignature Recipient's signature
     * @param recipientPubKey Recipient's public key
     * @return Claim script
     */
    public static Script createClaimScript(String secret, byte[] recipientSignature, byte[] recipientPubKey) {
        try {
            byte[] secretBytes = Utils.HEX.decode(secret);
            if (secretBytes.length != 32) {
                throw new IllegalArgumentException("Secret must be 32 bytes");
            }
            
            ScriptBuilder builder = new ScriptBuilder();
            
            // Signature and public key
            builder.data(recipientSignature);
            builder.data(recipientPubKey);
            
            // Secret (for OP_SHA256 verification)
            builder.data(secretBytes);
            
            // OP_TRUE to take the IF branch (claim path)
            builder.number(1);
            
            Script script = builder.build();
            log.debug("Created claim script: {}", script);
            
            return script;
        } catch (Exception e) {
            log.error("Error creating claim script", e);
            throw new RuntimeException("Failed to create claim script", e);
        }
    }
    
    /**
     * Create a refund script (spend from HTLC after locktime)
     * 
     * @param refundSignature Refunder's signature
     * @param refundPubKey Refunder's public key
     * @return Refund script
     */
    public static Script createRefundScript(byte[] refundSignature, byte[] refundPubKey) {
        ScriptBuilder builder = new ScriptBuilder();
        
        // Signature and public key
        builder.data(refundSignature);
        builder.data(refundPubKey);
        
        // OP_FALSE to take the ELSE branch (refund path)
        builder.number(0);
        
        Script script = builder.build();
        log.debug("Created refund script: {}", script);
        
        return script;
    }
    
    /**
     * Extract the contract address from an HTLC script
     * 
     * @param htlcScript HTLC script
     * @param networkParameters Network parameters
     * @return Contract address
     */
    public static Address getContractAddress(Script htlcScript, org.bitcoinj.core.NetworkParameters networkParameters) {
        try {
            // Create P2SH address from script
            // Hash the script and create a P2SH address
            byte[] scriptHash = org.bitcoinj.core.Utils.sha256hash160(htlcScript.getProgram());
            return LegacyAddress.fromP2SHHash(networkParameters, scriptHash);
        } catch (Exception e) {
            log.error("Error extracting contract address from HTLC script", e);
            throw new RuntimeException("Failed to extract contract address", e);
        }
    }
    
    /**
     * Verify that a secret matches a secret hash
     * 
     * @param secret Secret (hex encoded)
     * @param secretHash Secret hash (hex encoded)
     * @return true if secret matches hash
     */
    public static boolean verifySecret(String secret, String secretHash) {
        return HtlcUtils.verifySecret(secret, secretHash);
    }
}


