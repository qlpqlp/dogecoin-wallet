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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.params.DogecoinMainNetParams;
import org.libdohj.params.DogecoinTestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for multi-chain network parameters
 * 
 * Provides network parameters for Dogecoin, Bitcoin, and Litecoin
 * to support cross-chain atomic swaps.
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class MultiChainNetworkHelper {
    private static final Logger log = LoggerFactory.getLogger(MultiChainNetworkHelper.class);
    
    // Use mainnet for production
    private static final boolean USE_MAINNET = true;
    
    /**
     * Get network parameters for a given currency
     * 
     * @param currency Currency code ("DOGE", "BTC", or "LTC")
     * @return Network parameters for the currency
     */
    public static NetworkParameters getNetworkParameters(String currency) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        
        switch (currency.toUpperCase()) {
            case "DOGE":
                return USE_MAINNET ? DogecoinMainNetParams.get() : DogecoinTestNet3Params.get();
            case "BTC":
                return USE_MAINNET ? MainNetParams.get() : TestNet3Params.get();
            case "LTC":
                // Litecoin uses same network parameters as Bitcoin (bitcoinj compatible)
                // For full Litecoin support, would need litecoinj library
                // For now, use Bitcoin parameters (addresses are compatible)
                return USE_MAINNET ? MainNetParams.get() : TestNet3Params.get();
            default:
                throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
    }
    
    /**
     * Get block time in seconds for a given currency
     * 
     * @param currency Currency code
     * @return Block time in seconds
     */
    public static long getBlockTime(String currency) {
        switch (currency.toUpperCase()) {
            case "DOGE":
                return 60; // 1 minute
            case "BTC":
                return 600; // 10 minutes
            case "LTC":
                return 150; // 2.5 minutes
            default:
                return 600; // Default to 10 minutes
        }
    }
    
    /**
     * Calculate locktime in blocks for a given time period
     * 
     * @param currency Currency code
     * @param hours Number of hours
     * @return Locktime in blocks
     */
    public static long calculateLocktime(String currency, long hours) {
        long blockTime = getBlockTime(currency);
        long seconds = hours * 3600;
        return seconds / blockTime;
    }
    
    /**
     * Check if a currency is supported
     * 
     * @param currency Currency code
     * @return true if supported
     */
    public static boolean isSupported(String currency) {
        if (currency == null) {
            return false;
        }
        String upper = currency.toUpperCase();
        return "DOGE".equals(upper) || "BTC".equals(upper) || "LTC".equals(upper);
    }
    
    /**
     * Get the smallest unit value for a currency (e.g., satoshis for BTC)
     * 
     * @param currency Currency code
     * @return Smallest unit value (e.g., 100000000 for BTC/LTC, Coin.COIN.value for DOGE)
     */
    public static long getSmallestUnit(String currency) {
        switch (currency.toUpperCase()) {
            case "DOGE":
                return org.bitcoinj.core.Coin.COIN.value; // 100000000
            case "BTC":
                return 100000000L; // 1 BTC = 100,000,000 satoshis
            case "LTC":
                return 100000000L; // 1 LTC = 100,000,000 litoshis
            default:
                return 100000000L; // Default
        }
    }
}


