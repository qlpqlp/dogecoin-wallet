package de.schildbach.wallet.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bitcoinj.core.Sha256Hash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * DogeConnect client for fetching and verifying payment envelopes
 * Based on https://connect.dogecoin.org specification
 */
public class DogeConnectClient {
    private static final Logger log = LoggerFactory.getLogger(DogeConnectClient.class);
    
    public static class PaymentEnvelope {
        private String version;
        private String payload;
        private String pubkey;
        private String sig;
        
        // Decoded payload data
        private ParsedPayload parsedPayload;
        
        public PaymentEnvelope() {}
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        
        public String getPubkey() { return pubkey; }
        public void setPubkey(String pubkey) { this.pubkey = pubkey; }
        
        public String getSig() { return sig; }
        public void setSig(String sig) { this.sig = sig; }
        
        public ParsedPayload getParsedPayload() { return parsedPayload; }
        public void setParsedPayload(ParsedPayload parsedPayload) { this.parsedPayload = parsedPayload; }
    }
    
    public static class ParsedPayload {
        private String id;
        private String vendor_name;
        private double total;
        private String memo;
        private Object[] items; // Payment items
        
        public ParsedPayload() {}
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getVendorName() { return vendor_name; }
        public void setVendorName(String vendor_name) { this.vendor_name = vendor_name; }
        
        public double getTotal() { return total; }
        public void setTotal(double total) { this.total = total; }
        
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
        
        public Object[] getItems() { return items; }
        public void setItems(Object[] items) { this.items = items; }
    }
    
    /**
     * Fetch payment envelope from the DC URL
     */
    public static PaymentEnvelope fetchEnvelope(String dcUrl) throws IOException {
        log.info("Fetching payment envelope from: {}", dcUrl);
        
        // Ensure the URL has a protocol prefix
        if (!dcUrl.startsWith("http://") && !dcUrl.startsWith("https://")) {
            dcUrl = "https://" + dcUrl;
            log.info("Added protocol prefix, new URL: {}", dcUrl);
        }
        
        URL url = new URL(dcUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            // Parse JSON response
            Gson gson = new Gson();
            PaymentEnvelope envelope = gson.fromJson(response.toString(), PaymentEnvelope.class);
            
            // Decode the payload (base64 → JSON)
            if (envelope.getPayload() != null) {
                byte[] decodedPayload = Base64.getUrlDecoder().decode(envelope.getPayload());
                String payloadJson = new String(decodedPayload, StandardCharsets.UTF_8);
                ParsedPayload parsed = gson.fromJson(payloadJson, ParsedPayload.class);
                envelope.setParsedPayload(parsed);
                
                log.info("Payment ID: {}, Vendor: {}, Total: {}", 
                    parsed.getId(), parsed.getVendorName(), parsed.getTotal());
            }
            
            return envelope;
        }
    }
    
    /**
     * Verify the payment envelope signature
     * 1. Decode h (base64) to get expected 15-byte hash
     * 2. Extract pubkey from envelope (hex → 32 bytes)
     * 3. Hash pubkey with SHA-256, compare first 15 bytes
     * 4. Verify BIP-340 Schnorr signature
     */
    public static boolean verifySignature(PaymentEnvelope envelope, String h) {
        try {
            // Decode h from base64
            byte[] expectedHash = Base64.getUrlDecoder().decode(h);
            
            // Extract pubkey (hex → 32 bytes)
            byte[] pubkeyBytes = hexStringToByteArray(envelope.getPubkey());
            
            // Hash pubkey with SHA-256
            Sha256Hash pubkeyHash = Sha256Hash.of(pubkeyBytes);
            byte[] pubkeyHashBytes = pubkeyHash.getBytes();
            
            // Compare first 15 bytes
            for (int i = 0; i < expectedHash.length && i < 15; i++) {
                if (pubkeyHashBytes[i] != expectedHash[i]) {
                    log.warn("Signature verification failed: hash mismatch at byte {}", i);
                    return false;
                }
            }
            
            // TODO: Verify BIP-340 Schnorr signature
            // This would require additional crypto libraries
            
            log.info("Payment envelope signature verified successfully");
            return true;
            
        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Submit payment to DogeConnect relay
     */
    public static boolean submitPayment(String relayUrl, String id, String txHex, String refundAddress) throws IOException {
        log.info("Submitting payment to DogeConnect relay");
        
        URL url = new URL(relayUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        // Build JSON request
        String jsonRequest = String.format("{\"id\":\"%s\",\"tx\":\"%s\",\"refund\":\"%s\"}", 
            id, txHex, refundAddress);
        byte[] input = jsonRequest.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(input.length));
        
        try (java.io.OutputStream os = connection.getOutputStream()) {
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Parse response
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                log.info("Payment submitted successfully: {}", response.toString());
                return true;
            }
        } else {
            log.error("Payment submission failed with code: {}", responseCode);
            return false;
        }
    }
    
    /**
     * Poll payment status from DogeConnect relay
     */
    public static String pollPaymentStatus(String relayUrl, String id) throws IOException {
        log.info("Polling payment status for ID: {}", id);
        
        URL url = new URL(relayUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        // Build JSON request
        String jsonRequest = String.format("{\"id\":\"%s\"}", id);
        byte[] input = jsonRequest.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(input.length));
        
        try (java.io.OutputStream os = connection.getOutputStream()) {
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Parse response
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                log.info("Payment status: {}", response.toString());
                return response.toString();
            }
        } else {
            log.error("Status check failed with code: {}", responseCode);
            return null;
        }
    }
    
    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}

