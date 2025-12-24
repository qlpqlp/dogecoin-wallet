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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for querying blockchain APIs
 * 
 * Supports querying Bitcoin, Litecoin, and Dogecoin blockchains
 * for transaction and address information.
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class BlockchainApiHelper {
    private static final Logger log = LoggerFactory.getLogger(BlockchainApiHelper.class);
    
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private static final Gson gson = new Gson();
    
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
     * Query transaction status from blockchain
     * 
     * @param currency Currency code ("BTC", "LTC", or "DOGE")
     * @param txId Transaction ID
     * @return Transaction information
     */
    public static CompletableFuture<TransactionInfo> queryTransaction(String currency, String txId) {
        CompletableFuture<TransactionInfo> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                TransactionInfo info = new TransactionInfo(txId);
                
                switch (currency.toUpperCase()) {
                    case "BTC":
                        info = queryBitcoinTransaction(txId);
                        break;
                    case "LTC":
                        info = queryLitecoinTransaction(txId);
                        break;
                    case "DOGE":
                        info = queryDogecoinTransaction(txId);
                        break;
                    default:
                        future.completeExceptionally(new IllegalArgumentException("Unsupported currency: " + currency));
                        return;
                }
                
                future.complete(info);
            } catch (Exception e) {
                log.error("Error querying transaction for {}: {}", currency, txId, e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Query address information from blockchain
     * 
     * @param currency Currency code ("BTC", "LTC", or "DOGE")
     * @param address Address to query
     * @return Address information
     */
    public static CompletableFuture<AddressInfo> queryAddress(String currency, String address) {
        CompletableFuture<AddressInfo> future = new CompletableFuture<>();
        
        new Thread(() -> {
            try {
                AddressInfo info = new AddressInfo(address);
                
                switch (currency.toUpperCase()) {
                    case "BTC":
                        info = queryBitcoinAddress(address);
                        break;
                    case "LTC":
                        info = queryLitecoinAddress(address);
                        break;
                    case "DOGE":
                        info = queryDogecoinAddress(address);
                        break;
                    default:
                        future.completeExceptionally(new IllegalArgumentException("Unsupported currency: " + currency));
                        return;
                }
                
                future.complete(info);
            } catch (Exception e) {
                log.error("Error querying address for {}: {}", currency, address, e);
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    /**
     * Query Bitcoin transaction using Blockstream API
     */
    private static TransactionInfo queryBitcoinTransaction(String txId) throws IOException {
        // Use Blockstream API
        String url = "https://blockstream.info/api/tx/" + txId;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return new TransactionInfo(txId);
                }
                throw new IOException("Unexpected code: " + response);
            }
            
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            TransactionInfo info = new TransactionInfo(txId);
            info.exists = true;
            
            if (json.has("status")) {
                JsonObject status = json.getAsJsonObject("status");
                info.confirmed = status.has("confirmed") && status.get("confirmed").getAsBoolean();
                if (info.confirmed) {
                    info.blockHeight = status.has("block_height") ? status.get("block_height").getAsLong() : 0;
                    info.blockHash = status.has("block_hash") ? status.get("block_hash").getAsString() : null;
                    info.confirmations = status.has("block_height") ? (int) (getBitcoinBlockHeight() - status.get("block_height").getAsLong() + 1) : 0;
                }
            }
            
            if (json.has("status") && json.getAsJsonObject("status").has("block_time")) {
                info.timestamp = json.getAsJsonObject("status").get("block_time").getAsLong() * 1000;
            }
            
            return info;
        }
    }
    
    /**
     * Query Litecoin transaction using BlockCypher API
     */
    private static TransactionInfo queryLitecoinTransaction(String txId) throws IOException {
        // Use BlockCypher API for Litecoin
        String url = "https://api.blockcypher.com/v1/ltc/main/txs/" + txId;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return new TransactionInfo(txId);
                }
                throw new IOException("Unexpected code: " + response);
            }
            
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            TransactionInfo info = new TransactionInfo(txId);
            info.exists = true;
            info.confirmed = json.has("confirmed") && json.get("confirmed").getAsString() != null;
            
            if (info.confirmed) {
                info.blockHeight = json.has("block_height") ? json.get("block_height").getAsLong() : 0;
                info.confirmations = json.has("confirmations") ? json.get("confirmations").getAsInt() : 0;
                if (json.has("confirmed")) {
                    info.timestamp = json.get("confirmed").getAsLong() * 1000;
                }
            }
            
            return info;
        }
    }
    
    /**
     * Query Dogecoin transaction using BlockCypher API
     */
    private static TransactionInfo queryDogecoinTransaction(String txId) throws IOException {
        // Use BlockCypher API for Dogecoin
        String url = "https://api.blockcypher.com/v1/doge/main/txs/" + txId;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return new TransactionInfo(txId);
                }
                throw new IOException("Unexpected code: " + response);
            }
            
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            TransactionInfo info = new TransactionInfo(txId);
            info.exists = true;
            info.confirmed = json.has("confirmed") && json.get("confirmed").getAsString() != null;
            
            if (info.confirmed) {
                info.blockHeight = json.has("block_height") ? json.get("block_height").getAsLong() : 0;
                info.confirmations = json.has("confirmations") ? json.get("confirmations").getAsInt() : 0;
                if (json.has("confirmed")) {
                    info.timestamp = json.get("confirmed").getAsLong() * 1000;
                }
            }
            
            return info;
        }
    }
    
    /**
     * Query Bitcoin address using Blockstream API
     */
    private static AddressInfo queryBitcoinAddress(String address) throws IOException {
        String url = "https://blockstream.info/api/address/" + address;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new AddressInfo(address);
            }
            
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            AddressInfo info = new AddressInfo(address);
            info.hasTransactions = json.has("chain_stats") && json.getAsJsonObject("chain_stats").has("tx_count");
            
            if (info.hasTransactions) {
                JsonObject chainStats = json.getAsJsonObject("chain_stats");
                info.txCount = chainStats.has("tx_count") ? chainStats.get("tx_count").getAsInt() : 0;
                // Balance in satoshis
                long funded = chainStats.has("funded_txo_sum") ? chainStats.get("funded_txo_sum").getAsLong() : 0;
                long spent = chainStats.has("spent_txo_sum") ? chainStats.get("spent_txo_sum").getAsLong() : 0;
                info.balance = funded - spent;
            }
            
            return info;
        }
    }
    
    /**
     * Query Litecoin address using BlockCypher API
     */
    private static AddressInfo queryLitecoinAddress(String address) throws IOException {
        String url = "https://api.blockcypher.com/v1/ltc/main/addrs/" + address;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new AddressInfo(address);
            }
            
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            AddressInfo info = new AddressInfo(address);
            info.hasTransactions = json.has("n_tx") && json.get("n_tx").getAsInt() > 0;
            
            if (info.hasTransactions) {
                info.txCount = json.has("n_tx") ? json.get("n_tx").getAsInt() : 0;
                info.balance = json.has("balance") ? json.get("balance").getAsLong() : 0;
            }
            
            return info;
        }
    }
    
    /**
     * Query Dogecoin address using BlockCypher API
     */
    private static AddressInfo queryDogecoinAddress(String address) throws IOException {
        String url = "https://api.blockcypher.com/v1/doge/main/addrs/" + address;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return new AddressInfo(address);
            }
            
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            AddressInfo info = new AddressInfo(address);
            info.hasTransactions = json.has("n_tx") && json.get("n_tx").getAsInt() > 0;
            
            if (info.hasTransactions) {
                info.txCount = json.has("n_tx") ? json.get("n_tx").getAsInt() : 0;
                info.balance = json.has("balance") ? json.get("balance").getAsLong() : 0;
            }
            
            return info;
        }
    }
    
    /**
     * Get current Bitcoin block height
     */
    private static long getBitcoinBlockHeight() throws IOException {
        String url = "https://blockstream.info/api/blocks/tip/height";
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return 0;
            }
            
            String body = response.body().string();
            return Long.parseLong(body.trim());
        }
    }
}

