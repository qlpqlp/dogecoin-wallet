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

import android.os.AsyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to check RadioDoge logs for transaction confirmations
 */
public class RadioDogeLogChecker {
    private static final String RADIODOGE_LOGS_URL = "http://192.168.4.1/api/logs";
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    
    private static final Logger log = LoggerFactory.getLogger(RadioDogeLogChecker.class);
    
    /**
     * Check RadioDoge logs for transaction confirmation
     */
    public static void checkTransactionConfirmation(String transactionId, RadioDogeLogCallback callback) {
        new RadioDogeLogTask(callback).execute(transactionId);
    }
    
    /**
     * Callback interface for RadioDoge log check results
     */
    public interface RadioDogeLogCallback {
        void onTransactionConfirmed(String transactionId, String confirmationResult);
        void onTransactionError(String transactionId, String errorMessage);
        void onLogCheckFailed(String error);
    }
    
    /**
     * AsyncTask to check RadioDoge logs
     */
    private static class RadioDogeLogTask extends AsyncTask<String, Void, String> {
        private final RadioDogeLogCallback callback;
        private String transactionId;
        
        public RadioDogeLogTask(RadioDogeLogCallback callback) {
            this.callback = callback;
        }
        
        @Override
        protected String doInBackground(String... params) {
            this.transactionId = params[0];
            try {
                URL url = new URL(RADIODOGE_LOGS_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    String logsJson = response.toString();
                    log.info("RadioDoge logs retrieved: {}", logsJson);
                    
                    // Parse logs to find transaction confirmation
                    return parseLogsForTransaction(logsJson, transactionId);
                } else {
                    log.warn("RadioDoge logs API returned response code: {}", responseCode);
                    return "ERROR: API returned " + responseCode;
                }
            } catch (Exception e) {
                log.warn("Error checking RadioDoge logs: {}", e.getMessage());
                return "ERROR: " + e.getMessage();
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            if (result.startsWith("ERROR:")) {
                callback.onLogCheckFailed(result);
            } else if (result.startsWith("CONFIRMED:")) {
                String confirmationResult = result.substring("CONFIRMED:".length());
                callback.onTransactionConfirmed(transactionId, confirmationResult);
            } else if (result.startsWith("ERROR_TX:")) {
                String errorMessage = result.substring("ERROR_TX:".length());
                callback.onTransactionError(transactionId, errorMessage);
            } else {
                callback.onLogCheckFailed("No confirmation found in logs");
            }
        }
        
        private String parseLogsForTransaction(String logsJson, String transactionId) {
            try {
                // Look for DOGECOIN_RESPONSE patterns in the logs
                Pattern pattern = Pattern.compile("\\[\\d+\\]\\s*\\[DISPLAY\\]\\s*RX:\\s*Dconfirmation:DOGECOIN_RESPONSE:\\{.*?\"result\":\"([^\"]+)\".*?\"error\":([^,}]+).*?\\}");
                Matcher matcher = pattern.matcher(logsJson);
                
                String latestResult = null;
                String latestError = null;
                
                // Find the most recent DOGECOIN_RESPONSE
                while (matcher.find()) {
                    String result = matcher.group(1);
                    String error = matcher.group(2);
                    
                    if (result != null && !result.isEmpty()) {
                        latestResult = result;
                    }
                    if (error != null && !error.equals("null")) {
                        latestError = error;
                    }
                }
                
                if (latestResult != null) {
                    log.info("Found transaction confirmation in RadioDoge logs: {}", latestResult);
                    return "CONFIRMED:" + latestResult;
                } else if (latestError != null) {
                    log.info("Found transaction error in RadioDoge logs: {}", latestError);
                    return "ERROR_TX:" + latestError;
                } else {
                    log.info("No DOGECOIN_RESPONSE found in RadioDoge logs for transaction: {}", transactionId);
                    return "ERROR: No confirmation found";
                }
            } catch (Exception e) {
                log.warn("Error parsing RadioDoge logs: {}", e.getMessage());
                return "ERROR: " + e.getMessage();
            }
        }
    }
}
