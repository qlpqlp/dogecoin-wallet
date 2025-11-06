package de.schildbach.wallet.service;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.format.Formatter;
import com.google.gson.Gson;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple HTTP API server for POS integration with Payment Terminal Mode
 * 
 * This service runs a minimal HTTP server that accepts payment requests from POS systems
 * and returns payment information (address, amount, QR code data)
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class PaymentTerminalApiService {
    private static final Logger log = LoggerFactory.getLogger(PaymentTerminalApiService.class);
    private static final int PORT = 6900;
    
    private ServerSocket serverSocket;
    private HandlerThread serverThread;
    private Handler serverHandler;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    
    private WalletApplication application;
    private Configuration config;
    
    // Callback interface for UI updates
    public interface PaymentRequestCallback {
        void onPaymentRequestReceived(Address address, Coin amount, String uri);
    }
    
    private PaymentRequestCallback callback;
    
    public PaymentTerminalApiService(WalletApplication application) {
        this.application = application;
        this.config = application.getConfiguration();
    }
    
    public void setPaymentRequestCallback(PaymentRequestCallback callback) {
        this.callback = callback;
    }
    
    public void start() {
        if (isRunning.getAndSet(true)) {
            log.warn("Payment API service already running");
            return;
        }
        
        serverThread = new HandlerThread("PaymentTerminalApiService");
        serverThread.start();
        serverHandler = new Handler(serverThread.getLooper());
        
        serverHandler.post(() -> {
            try {
                InetAddress addr = InetAddress.getByName("0.0.0.0");
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress(addr, PORT));
                log.info("Payment Terminal API server started on port {}", PORT);
                
                while (isRunning.get() && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleClient(clientSocket);
                    } catch (IOException e) {
                        if (isRunning.get()) {
                            log.error("Error accepting connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Error starting Payment Terminal API server", e);
            }
        });
    }
    
    public void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }
        
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error closing server socket", e);
        }
        
        if (serverThread != null) {
            serverThread.quitSafely();
        }
        
        log.info("Payment Terminal API server stopped");
    }
    
    private void handleClient(final Socket clientSocket) {
        new Thread(() -> {
            try {
                processRequest(clientSocket);
            } catch (IOException e) {
                log.error("Error processing request", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.error("Error closing client socket", e);
                }
            }
        }).start();
    }
    
    private void processRequest(final Socket clientSocket) throws IOException {
        // Simple HTTP request parsing
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(clientSocket.getInputStream()));
        
        String line;
        String requestLine = null;
        StringBuilder request = new StringBuilder();
        
        // Read headers
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (requestLine == null) {
                requestLine = line;
            }
            request.append(line).append("\r\n");
        }
        
        if (requestLine == null) {
            return;
        }
        
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return;
        }
        
        String method = parts[0];
        String path = parts[1];
        
        log.info("API request: {} {}", method, path);
        
        // Read body for POST requests
        String requestBody = "";
        if (method.equals("POST")) {
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while (reader.ready() && (read = reader.read(buffer)) > 0) {
                body.append(buffer, 0, read);
            }
            requestBody = body.toString();
            log.info("Request body: {}", requestBody);
        }
        
        String response;
        
        if (method.equals("GET") && path.equals("/payment-request")) {
            // Generate a new payment request
            response = handlePaymentRequest();
        } else if (method.equals("POST") && path.equals("/payment-request")) {
            // Create a payment request for a specific amount
            response = handlePaymentRequestWithAmount(requestBody);
        } else if (method.equals("GET") && path.equals("/status")) {
            response = handleStatus();
        } else {
            response = "HTTP/1.1 404 Not Found\r\n\r\n";
        }
        
        java.io.OutputStream outputStream = clientSocket.getOutputStream();
        outputStream.write(response.getBytes());
        outputStream.flush();
    }
    
    private String handlePaymentRequest() {
        try {
            if (application == null) {
                return createErrorResponse("Application context missing");
            }
            Wallet wallet = application.getWallet();
            if (wallet == null) {
                return createErrorResponse("Wallet not initialized");
            }
            
            // Use a simpler approach - get the address directly from the main thread
            // where the BitcoinJ context is already available
            final Address[] addressResult = new Address[1];
            final Exception[] exceptionResult = new Exception[1];
            
            // Run on the main thread where BitcoinJ context is available
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    addressResult[0] = wallet.freshReceiveAddress();
                } catch (Exception e) {
                    exceptionResult[0] = e;
                }
            });
            
            // Wait for the operation to complete (with timeout)
            long startTime = System.currentTimeMillis();
            while (addressResult[0] == null && exceptionResult[0] == null && 
                   (System.currentTimeMillis() - startTime) < 5000) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return createErrorResponse("Operation interrupted");
                }
            }
            
            if (exceptionResult[0] != null) {
                log.error("Error getting fresh address", exceptionResult[0]);
                return createErrorResponse("Failed to generate address: " + exceptionResult[0].getMessage());
            }
            
            if (addressResult[0] == null) {
                return createErrorResponse("Timeout waiting for address generation");
            }
            
            Address address = addressResult[0];
            
            // Create Bitcoin URI for QR code
            String uri = String.format("dogecoin:%s", address.toString());
            
            // Trigger UI callback to display QR code
            if (callback != null) {
                callback.onPaymentRequestReceived(address, Coin.ZERO, uri); // Assuming GET /payment-request doesn't have an amount
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("address", address.toString());
            response.put("uri", uri);
            response.put("currency", "DOGE");
            
            Gson gson = new Gson();
            String json = gson.toJson(response);
            return createJsonResponse(json);
        } catch (Exception e) {
            log.error("Error handling payment request", e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private String handlePaymentRequestWithAmount(String requestBody) {
        try {
            log.info("handlePaymentRequestWithAmount called - application: {}, config: {}", 
                application != null ? "not null" : "null", 
                config != null ? "not null" : "null");
                
            if (requestBody == null || requestBody.trim().isEmpty()) {
                return createErrorResponse("No request body provided");
            }
            
            log.info("Processing request body: {}", requestBody);
            
            Gson gson = new Gson();
            Map request = gson.fromJson(requestBody, Map.class);
            
            if (!request.containsKey("amount")) {
                return createErrorResponse("Amount required");
            }
            
            double amount = Double.parseDouble(request.get("amount").toString());
            Coin coinAmount = Coin.valueOf((long) (amount * Coin.COIN.value));
            
            // Try to get application context from WalletApplication.getInstance()
            if (application == null) {
                log.warn("Application context is null, trying to get instance");
                try {
                    // This is a fallback - try to get the application instance
                    // Note: This might not work in all cases
                    return createErrorResponse("Application not ready - please wait a moment and try again");
                } catch (Exception e) {
                    return createErrorResponse("Application context missing: " + e.getMessage());
                }
            }
            
            Wallet wallet = application.getWallet();
            if (wallet == null) {
                return createErrorResponse("Wallet not initialized");
            }
            
            // Use a simpler approach - get the address directly from the main thread
            // where the BitcoinJ context is already available
            final Address[] addressResult = new Address[1];
            final Exception[] exceptionResult = new Exception[1];
            
            // Run on the main thread where BitcoinJ context is available
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    addressResult[0] = wallet.freshReceiveAddress();
                } catch (Exception e) {
                    exceptionResult[0] = e;
                }
            });
            
            // Wait for the operation to complete (with timeout)
            long startTime = System.currentTimeMillis();
            while (addressResult[0] == null && exceptionResult[0] == null && 
                   (System.currentTimeMillis() - startTime) < 5000) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return createErrorResponse("Operation interrupted");
                }
            }
            
            if (exceptionResult[0] != null) {
                log.error("Error getting fresh address", exceptionResult[0]);
                return createErrorResponse("Failed to generate address: " + exceptionResult[0].getMessage());
            }
            
            if (addressResult[0] == null) {
                return createErrorResponse("Timeout waiting for address generation");
            }
            
            Address address = addressResult[0];
            
            // Create Bitcoin URI for QR code
            String amountStr = String.valueOf((double) coinAmount.value / Coin.COIN.value);
            String uri = String.format("dogecoin:%s?amount=%s", address.toString(), amountStr);
            
            // Trigger UI callback to display QR code
            if (callback != null) {
                callback.onPaymentRequestReceived(address, coinAmount, uri);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("address", address.toString());
            response.put("amount", String.valueOf(amount));
            response.put("uri", uri);
            response.put("currency", "DOGE");
            
            String json = gson.toJson(response);
            return createJsonResponse(json);
        } catch (Exception e) {
            log.error("Error handling payment request with amount", e);
            return createErrorResponse(e.getMessage());
        }
    }
    
    private String handleStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "running");
        response.put("terminal_mode", config.getPaymentTerminalEnabled());
        response.put("port", PORT);
        
        Gson gson = new Gson();
        String json = gson.toJson(response);
        
        return createJsonResponse(json);
    }
    
    private String createJsonResponse(String json) {
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: application/json\r\n" +
               "Content-Length: " + json.length() + "\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "\r\n" +
               json;
    }
    
    private String createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        
        Gson gson = new Gson();
        String json = gson.toJson(response);
        
        return createJsonResponse(json);
    }
    
    public int getPort() {
        return PORT;
    }
    
    public boolean isRunning() {
        return isRunning.get();
    }
}

