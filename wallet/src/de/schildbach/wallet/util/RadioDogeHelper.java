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

import android.content.Context;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Helper class for RadioDoge integration
 * Checks if connected to RadioDoge WiFi and broadcasts transactions via HTTP POST
 */
public class RadioDogeHelper {
    private static final String RADIODOGE_SSID = "RadioDoge";
    private static final String RADIODOGE_API_URL = "http://192.168.4.1/api/broadcast";
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    
    private static final Logger log = LoggerFactory.getLogger(RadioDogeHelper.class);

    /**
     * Check if the device is connected to RadioDoge WiFi network
     * Uses multiple methods to detect RadioDoge network due to Android SSID restrictions
     */
    public static boolean isConnectedToRadioDoge(Context context) {
        try {
            // Method 1: Try to get SSID from WifiManager
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    String ssid = wifiInfo.getSSID();
                    if (ssid != null && !ssid.equals("<unknown ssid>")) {
                        // Remove quotes if present
                        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                            ssid = ssid.substring(1, ssid.length() - 1);
                        }
                        boolean isRadioDoge = RADIODOGE_SSID.equals(ssid);
                        log.info("Current WiFi SSID: '{}', is RadioDoge: {}", ssid, isRadioDoge);
                        return isRadioDoge;
                    }
                }
            }

            // Method 2: Check if we can reach the RadioDoge API endpoint
            // This is a more reliable way to detect RadioDoge network
            try {
                java.net.URL url = new java.net.URL(RADIODOGE_API_URL);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(2000); // Short timeout for local network
                connection.setReadTimeout(2000);
                connection.setInstanceFollowRedirects(false);
                
                int responseCode = connection.getResponseCode();
                boolean canReachRadioDoge = (responseCode >= 200 && responseCode < 500); // Any response means we can reach it
                
                log.info("RadioDoge API reachable: {} (response code: {})", canReachRadioDoge, responseCode);
                return canReachRadioDoge;
            } catch (Exception e) {
                log.info("Cannot reach RadioDoge API: {}", e.getMessage());
            }

            // Method 3: Check network configuration for RadioDoge-like patterns
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager != null) {
                    NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
                    if (activeNetwork != null && activeNetwork.isConnected() && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                        // Check if we're on a local network that could be RadioDoge
                        // RadioDoge typically uses 192.168.4.x network
                        java.net.InetAddress localAddress = java.net.InetAddress.getLocalHost();
                        String hostAddress = localAddress.getHostAddress();
                        boolean isLocalNetwork = hostAddress != null && hostAddress.startsWith("192.168.4.");
                        
                        log.info("Local network address: {}, is 192.168.4.x: {}", hostAddress, isLocalNetwork);
                        return isLocalNetwork;
                    }
                }
            } catch (Exception e) {
                log.info("Error checking local network: {}", e.getMessage());
            }

            log.info("Could not determine if connected to RadioDoge WiFi");
            return false;
        } catch (Exception e) {
            log.warn("Error checking WiFi connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if device has internet connectivity by testing actual internet access
     * Note: RadioDoge networks use LoRa and don't provide internet access
     */
    public static boolean hasInternetConnectivity(Context context) {
        try {
            // First check if we're connected to RadioDoge WiFi - if so, assume no internet
            if (isConnectedToRadioDoge(context)) {
                log.info("Internet connectivity: false (connected to RadioDoge WiFi - uses LoRa, no internet)");
                return false;
            }
            
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }

            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnected()) {
                log.info("Internet connectivity: false (no active network)");
                return false;
            }
            
            // Test actual internet connectivity by trying to reach a reliable endpoint
            try {
                java.net.URL url = new java.net.URL("http://www.google.com");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                
                int responseCode = connection.getResponseCode();
                boolean hasInternet = (responseCode >= 200 && responseCode < 400);
                
                log.info("Internet connectivity: {} (response code: {})", hasInternet, responseCode);
                return hasInternet;
            } catch (Exception e) {
                log.info("Internet connectivity: false (connection test failed: {})", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.warn("Error checking internet connectivity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Broadcast transaction via RadioDoge
     */
    public static void broadcastTransactionViaRadioDoge(Transaction transaction, RadioDogeCallback callback) {
        new RadioDogeBroadcastTask(callback).execute(transaction);
    }
    
    /**
     * Broadcast transaction via RadioDoge and check logs for confirmation
     */
    public static void broadcastTransactionViaRadioDogeWithConfirmation(Transaction transaction, RadioDogeCallback callback) {
        new RadioDogeBroadcastWithConfirmationTask(callback).execute(transaction);
    }

    /**
     * Callback interface for RadioDoge broadcast results
     */
    public interface RadioDogeCallback {
        void onSuccess();
        void onFailure(String error);
    }

    /**
     * AsyncTask to handle RadioDoge HTTP POST request
     */
    private static class RadioDogeBroadcastTask extends AsyncTask<Transaction, Void, String> {
        private final RadioDogeCallback callback;

        public RadioDogeBroadcastTask(RadioDogeCallback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(Transaction... transactions) {
            if (transactions.length == 0) {
                return "No transaction provided";
            }

            Transaction transaction = transactions[0];
            
            try {
                // Convert transaction to hex string
                String transactionHex = Utils.HEX.encode(transaction.bitcoinSerialize());
                log.info("Broadcasting transaction via RadioDoge: {}", transaction.getTxId());
                
                // Prepare POST data
                String postData = "type=transaction&priority=normal&message=" + transactionHex;
                byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);

                // Create HTTP connection
                URL url = new URL(RADIODOGE_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
                connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setDoOutput(true);

                // Send POST data
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(postDataBytes);
                    os.flush();
                }

                // Get response
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    log.info("RadioDoge broadcast successful, response code: {}", responseCode);
                    return null; // Success
                } else {
                    String error = "RadioDoge API returned error code: " + responseCode;
                    log.warn(error);
                    return error;
                }

            } catch (IOException e) {
                String error = "RadioDoge broadcast failed: " + e.getMessage();
                log.warn(error, e);
                return error;
            } catch (Exception e) {
                String error = "Unexpected error during RadioDoge broadcast: " + e.getMessage();
                log.error(error, e);
                return error;
            }
        }

        @Override
        protected void onPostExecute(String error) {
            if (error == null) {
                callback.onSuccess();
            } else {
                callback.onFailure(error);
            }
        }
    }
    
    /**
     * AsyncTask to handle RadioDoge HTTP POST request with log checking
     */
    private static class RadioDogeBroadcastWithConfirmationTask extends AsyncTask<Transaction, Void, String> {
        private final RadioDogeCallback callback;

        public RadioDogeBroadcastWithConfirmationTask(RadioDogeCallback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(Transaction... transactions) {
            Transaction transaction = transactions[0];
            try {
                log.info("Broadcasting transaction via RadioDoge: {}", transaction.getTxId());
                
                // First, broadcast the transaction
                java.net.URL url = new java.net.URL(RADIODOGE_API_URL);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                // Prepare the transaction data
                String transactionHex = org.bitcoinj.core.Utils.HEX.encode(transaction.bitcoinSerialize());
                String postData = "type=transaction&priority=normal&message=" + transactionHex;

                // Send the request
                try (java.io.OutputStream os = connection.getOutputStream()) {
                    byte[] input = postData.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    log.info("RadioDoge broadcast successful, response code: {}", responseCode);
                    
                    // Wait a bit for the transaction to be processed
                    Thread.sleep(3000);
                    
                    // Check logs for confirmation
                    return checkLogsForConfirmation(transaction.getTxId().toString());
                } else {
                    log.warn("RadioDoge broadcast failed, response code: {}", responseCode);
                    return "ERROR: HTTP " + responseCode;
                }
            } catch (Exception e) {
                log.warn("Error broadcasting transaction via RadioDoge: {}", e.getMessage());
                return "ERROR: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.startsWith("ERROR:")) {
                callback.onFailure(result);
            } else if (result.startsWith("CONFIRMED:")) {
                String confirmationResult = result.substring("CONFIRMED:".length());
                log.info("Transaction confirmed via RadioDoge logs: {}", confirmationResult);
                callback.onSuccess();
            } else {
                // Broadcast successful but no confirmation found yet
                log.info("Transaction broadcast successful but no confirmation found in logs yet");
                callback.onSuccess();
            }
        }
        
        private String checkLogsForConfirmation(String transactionId) {
            try {
                java.net.URL logsUrl = new java.net.URL("http://192.168.4.1/api/logs");
                java.net.HttpURLConnection logsConnection = (java.net.HttpURLConnection) logsUrl.openConnection();
                logsConnection.setRequestMethod("GET");
                logsConnection.setConnectTimeout(2000);
                logsConnection.setReadTimeout(2000);
                
                int logsResponseCode = logsConnection.getResponseCode();
                if (logsResponseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(logsConnection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    String logsJson = response.toString();
                    log.info("RadioDoge logs retrieved for confirmation check of transaction: {}", transactionId);
                    
                    // Parse JSON to get logs array
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(logsJson);
                    org.json.JSONArray logsArray = jsonResponse.getJSONArray("logs");
                    
                    // Look for DOGECOIN_RESPONSE patterns that match our specific transaction
                    for (int i = 0; i < logsArray.length(); i++) {
                        String logEntry = logsArray.getString(i);
                        
                        if (logEntry.contains("DOGECOIN_RESPONSE")) {
                            // Check for successful result (transaction hash)
                            if (logEntry.contains("\"result\":\"") && !logEntry.contains("\"result\":null")) {
                                String resultPattern = "\"result\":\"([a-f0-9]+)\"";
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(resultPattern);
                                java.util.regex.Matcher matcher = pattern.matcher(logEntry);
                                if (matcher.find()) {
                                    String resultHash = matcher.group(1);
                                    // Check if this result matches our specific transaction
                                    if (resultHash.equals(transactionId)) {
                                        return "CONFIRMED:" + resultHash;
                                    }
                                }
                            }
                            // Check for "transaction already in block chain" (also success)
                            if (logEntry.contains("transaction already in block chain")) {
                                // For "already in blockchain" errors, we can't match by hash
                                // but we can check if this log entry is recent enough to be relevant
                                // For now, we'll consider any "already in blockchain" as a success
                                // since it means the transaction was processed
                                return "CONFIRMED:already_in_blockchain";
                            }
                        }
                    }
                    
                    return "NO_CONFIRMATION";
                } else {
                    return "ERROR: Logs API returned " + logsResponseCode;
                }
            } catch (Exception e) {
                log.warn("Error checking RadioDoge logs: {}", e.getMessage());
                return "ERROR: " + e.getMessage();
            }
        }
    }
}
