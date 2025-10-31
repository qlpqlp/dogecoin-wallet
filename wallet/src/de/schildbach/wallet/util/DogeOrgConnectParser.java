package de.schildbach.wallet.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.data.DogeOrgConnectEnvelope;
import de.schildbach.wallet.data.PaymentIntent;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Parser for DogeOrg Connect payment envelopes
 * Based on specification at https://connect.dogecoin.org
 */
public class DogeOrgConnectParser {
    private static final Logger log = LoggerFactory.getLogger(DogeOrgConnectParser.class);
    
    private static final String CONNECT_SCHEME = "dogecoinconnect://";
    private static final int MAX_QR_CODE_SIZE = 2953; // Maximum QR code capacity
    
    /**
     * Check if the input is a DogeOrg Connect format
     */
    public static boolean isDogeOrgConnectFormat(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        // Check for dogecoinconnect:// scheme
        if (input.startsWith(CONNECT_SCHEME)) {
            return true;
        }
        
        // Check for JSON envelope embedded in QR code (base64 encoded)
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(input), StandardCharsets.UTF_8);
            if (decoded.trim().startsWith("{") && decoded.contains("\"vendor\"") && decoded.contains("\"items\"")) {
                return true;
            }
        } catch (Exception e) {
            // Not a valid base64 or not a JSON envelope
        }
        
        // Check for JSON envelope directly
        if (input.trim().startsWith("{") && input.contains("\"vendor\"") && input.contains("\"items\"")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Parse a DogeOrg Connect payment envelope from various formats
     */
    public static DogeOrgConnectEnvelope parse(String input) throws Exception {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }
        
        String envelopeJson;
        
        // Handle dogecoinconnect:// scheme
        if (input.startsWith(CONNECT_SCHEME)) {
            String data = input.substring(CONNECT_SCHEME.length());
            envelopeJson = decodeConnectData(data);
        } else {
            // Try to decode as base64 first
            try {
                envelopeJson = new String(Base64.getUrlDecoder().decode(input), StandardCharsets.UTF_8);
            } catch (Exception e) {
                // If not base64, assume it's JSON directly
                envelopeJson = input;
            }
        }
        
        log.info("Parsing DogeOrg Connect envelope");
        
        // Parse JSON
        Gson gson = new Gson();
        JsonParser parser = new JsonParser();
        JsonObject envelope = parser.parse(envelopeJson).getAsJsonObject();
        
        DogeOrgConnectEnvelope result = new DogeOrgConnectEnvelope();
        
        // Parse vendor information
        if (envelope.has("vendor")) {
            JsonObject vendorObj = envelope.getAsJsonObject("vendor");
            DogeOrgConnectEnvelope.Vendor vendor = new DogeOrgConnectEnvelope.Vendor();
            
            if (vendorObj.has("name")) {
                vendor.setName(vendorObj.get("name").getAsString());
            }
            if (vendorObj.has("address")) {
                vendor.setAddress(vendorObj.get("address").getAsString());
            }
            if (vendorObj.has("website")) {
                vendor.setWebsite(vendorObj.get("website").getAsString());
            }
            if (vendorObj.has("contact")) {
                vendor.setContact(vendorObj.get("contact").getAsString());
            }
            
            result.setVendor(vendor);
        }
        
        // Parse payment items
        if (envelope.has("items")) {
            JsonArray itemsArray = envelope.getAsJsonArray("items");
            List<DogeOrgConnectEnvelope.Item> items = new ArrayList<>();
            
            for (int i = 0; i < itemsArray.size(); i++) {
                JsonObject itemObj = itemsArray.get(i).getAsJsonObject();
                DogeOrgConnectEnvelope.Item item = new DogeOrgConnectEnvelope.Item();
                
                if (itemObj.has("name")) {
                    item.setName(itemObj.get("name").getAsString());
                }
                if (itemObj.has("description")) {
                    item.setDescription(itemObj.get("description").getAsString());
                }
                if (itemObj.has("price")) {
                    item.setPrice(itemObj.get("price").getAsDouble());
                }
                if (itemObj.has("quantity")) {
                    item.setQuantity(itemObj.get("quantity").getAsInt());
                }
                
                items.add(item);
            }
            
            result.setItems(items);
            
            // Calculate totals
            double subtotal = 0.0;
            for (DogeOrgConnectEnvelope.Item item : items) {
                subtotal += item.getTotal();
            }
            result.setSubtotal(subtotal);
        }
        
        // Parse amounts
        if (envelope.has("subtotal")) {
            result.setSubtotal(envelope.get("subtotal").getAsDouble());
        }
        if (envelope.has("tax")) {
            result.setTax(envelope.get("tax").getAsDouble());
        }
        if (envelope.has("fees")) {
            result.setFees(envelope.get("fees").getAsDouble());
        }
        if (envelope.has("total")) {
            result.setTotal(envelope.get("total").getAsDouble());
        }
        
        // Parse Dogecoin amount
        if (envelope.has("amount")) {
            long amount = envelope.get("amount").getAsLong();
            result.setDogecoinAmount(amount);
        }
        
        // Parse metadata
        if (envelope.has("memo")) {
            result.setMemo(envelope.get("memo").getAsString());
        }
        if (envelope.has("invoiceNumber")) {
            result.setInvoiceNumber(envelope.get("invoiceNumber").getAsString());
        }
        
        // Parse signature
        if (envelope.has("signature")) {
            result.setSignature(envelope.get("signature").getAsString());
        }
        
        log.info("Parsed DogeOrg Connect envelope with {} items, total: {} DOGE", 
            result.getItems() != null ? result.getItems().size() : 0, 
            Coin.valueOf(result.getDogecoinAmount()).toFriendlyString());
        
        return result;
    }
    
    /**
     * Convert DogeOrg Connect envelope to PaymentIntent
     */
    public static PaymentIntent toPaymentIntent(DogeOrgConnectEnvelope envelope) {
        // Get the destination address from the envelope
        // This should be included in the envelope
        String destinationAddress = extractDestinationAddress(envelope);
        
        if (destinationAddress == null) {
            throw new IllegalArgumentException("Destination address not found in envelope");
        }
        
        // Create payment intent with detailed information
        Address address = Address.fromString(Constants.NETWORK_PARAMETERS, destinationAddress);
        Coin amount = Coin.valueOf(envelope.getDogecoinAmount());
        
        // Build detailed memo with itemization
        StringBuilder memoBuilder = new StringBuilder();
        if (envelope.getVendor() != null && envelope.getVendor().getName() != null) {
            memoBuilder.append("From: ").append(envelope.getVendor().getName()).append("\n");
        }
        
        if (envelope.getItems() != null && !envelope.getItems().isEmpty()) {
            memoBuilder.append("Items:\n");
            for (DogeOrgConnectEnvelope.Item item : envelope.getItems()) {
                memoBuilder.append(String.format("- %s (x%d): %.2f\n", 
                    item.getName(), item.getQuantity(), item.getPrice()));
            }
        }
        
        if (envelope.getTax() > 0) {
            memoBuilder.append(String.format("Tax: %.2f\n", envelope.getTax()));
        }
        if (envelope.getFees() > 0) {
            memoBuilder.append(String.format("Fees: %.2f\n", envelope.getFees()));
        }
        memoBuilder.append(String.format("Total: %.2f\n", envelope.getTotal()));
        
        if (envelope.getMemo() != null) {
            memoBuilder.append("\n").append(envelope.getMemo());
        }
        
        // Create OP_RETURN output with reference data if available
        Script outputScript = ScriptBuilder.createOutputScript(address);
        
        PaymentIntent.Output[] outputs = new PaymentIntent.Output[] {
            new PaymentIntent.Output(amount, outputScript)
        };
        
        return new PaymentIntent(
            PaymentIntent.Standard.DOGEORG_CONNECT,
            null, // pkiName
            null, // pkiCaName
            outputs,
            memoBuilder.toString(), // memo
            null, // bluetoothMac
            null, // paymentUrl
            null, // merchantData
            null // paymentRequestHash
        );
    }
    
    /**
     * Verify vendor signature
     */
    public static boolean verifySignature(DogeOrgConnectEnvelope envelope) {
        // TODO: Implement signature verification
        // This would involve verifying the vendor's digital signature
        // against the payment envelope data
        log.info("Signature verification not yet implemented");
        return true; // For now, accept all signatures
    }
    
    /**
     * Decode connect data from scheme format
     */
    private static String decodeConnectData(String data) {
        // Handle different encoding formats
        try {
            // Try base64url decoding
            return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If not base64, return as-is
            return data;
        }
    }
    
    /**
     * Extract destination address from envelope
     */
    private static String extractDestinationAddress(DogeOrgConnectEnvelope envelope) {
        // The destination address should be in the envelope JSON
        // For now, we'll need to look for it in the metadata
        // This would need to be added to the envelope structure
        return null; // TODO: Implement address extraction
    }
}

