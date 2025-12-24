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

package de.schildbach.wallet.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.Category;
import de.schildbach.wallet.data.Product;
import de.schildbach.wallet.util.Qr;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Base64;

/**
 * Point of Sale Web Service
 * 
 * Serves a web interface for customers to browse products and make payments
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class PointOfSaleWebService {
    private static final Logger log = LoggerFactory.getLogger(PointOfSaleWebService.class);
    private static final int PORT = 4200;
    
    private ServerSocket serverSocket;
    private HandlerThread serverThread;
    private Handler serverHandler;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    
    private WalletApplication application;
    private Configuration config;
    private AddressBookDatabase database;
    private Wallet wallet;
    
    // Map of payment addresses to product IDs for tracking payments
    private Map<String, PaymentInfo> pendingPayments = new HashMap<>();
    
    private Gson gson = new Gson();
    
    public PointOfSaleWebService(WalletApplication application) {
        this.application = application;
        this.config = application.getConfiguration();
        this.database = AddressBookDatabase.getDatabase(application);
    }
    
    public void start() {
        if (isRunning.getAndSet(true)) {
            log.warn("POS web service already running");
            return;
        }
        
        // Get wallet reference
        application.getWalletAsync(w -> {
            this.wallet = w;
            if (w != null) {
                // Listen for payments
                w.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
                    @Override
                    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                        handlePayment(tx);
                    }
                });
            }
        });
        
        serverThread = new HandlerThread("PointOfSaleWebService");
        serverThread.start();
        serverHandler = new Handler(serverThread.getLooper());
        
        serverHandler.post(() -> {
            try {
                InetAddress addr = InetAddress.getByName("0.0.0.0");
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress(addr, PORT));
                log.info("POS Web Service started on port {}", PORT);
                
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
                log.error("Error starting POS Web Service", e);
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
        
        log.info("POS Web Service stopped");
    }
    
    public int getPort() {
        return PORT;
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
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(clientSocket.getInputStream()));
        
        String line;
        String requestLine = null;
        
        // Read headers
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (requestLine == null) {
                requestLine = line;
            }
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
        
        log.info("POS Web request: {} {}", method, path);
        
        String response;
        
        if (path.equals("/") || path.equals("/index.html")) {
            response = handleHomePage();
        } else if (path.startsWith("/category/")) {
            String categoryId = path.substring("/category/".length()).split("\\?")[0];
            response = handleCategoryPage(categoryId);
        } else if (path.startsWith("/pay/")) {
            String productId = path.substring("/pay/".length()).split("\\?")[0];
            Map<String, String> params = parseQueryString(path);
            String quantityStr = params.get("quantity");
            response = handlePaymentPage(productId, quantityStr);
        } else if (path.startsWith("/product/")) {
            String productId = path.substring("/product/".length()).split("\\?")[0];
            Map<String, String> params = parseQueryString(path);
            String quantityStr = params.get("quantity");
            response = handleProductPage(productId, quantityStr);
        } else if (path.startsWith("/api/")) {
            response = handleApiRequest(path, method, reader);
        } else {
            response = "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\n\r\n404 Not Found";
        }
        
        java.io.OutputStream outputStream = clientSocket.getOutputStream();
        outputStream.write(response.getBytes());
        outputStream.flush();
    }
    
    private String handleHomePage() {
        List<Category> categories = database.categoryDao().getAllCategories();
        List<Long> categoriesWithProducts = database.productDao().getCategoriesWithAvailableProducts();
        
        StringBuilder html = new StringBuilder();
        html.append("HTTP/1.1 200 OK\r\n");
        html.append("Content-Type: text/html; charset=utf-8\r\n\r\n");
        html.append(getHtmlHeader("Point of Sale - Categories"));
        html.append("<body>");
        html.append("<div class='container'>");
        html.append("<h1>Point of Sale</h1>");
        html.append("<div class='categories'>");
        
        for (Category category : categories) {
            if (categoriesWithProducts.contains(category.getId())) {
                html.append("<a href='/category/").append(category.getId()).append("' class='category-card'>");
                html.append("<h2>").append(escapeHtml(category.getName())).append("</h2>");
                html.append("</a>");
            }
        }
        
        html.append("</div>");
        html.append("</div>");
        html.append(getHtmlFooter());
        html.append("</body></html>");
        
        return html.toString();
    }
    
    private String handleCategoryPage(String categoryIdStr) {
        try {
            long categoryId = Long.parseLong(categoryIdStr);
            Category category = database.categoryDao().getCategoryById(categoryId);
            if (category == null) {
                return "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\n\r\n404 Category Not Found";
            }
            
            List<Product> products = database.productDao().getAvailableProductsByCategory(categoryId);
            
            StringBuilder html = new StringBuilder();
            html.append("HTTP/1.1 200 OK\r\n");
            html.append("Content-Type: text/html; charset=utf-8\r\n\r\n");
            html.append(getHtmlHeader("Category: " + category.getName()));
            html.append("<body>");
            html.append("<div class='container'>");
            html.append("<a href='/' class='back-link'>← Back to Categories</a>");
            html.append("<h1>").append(escapeHtml(category.getName())).append("</h1>");
            html.append("<div class='products'>");
            
            for (Product product : products) {
                html.append("<div class=\"product-card\">");
                
                if (product.getImagePath() != null && !product.getImagePath().isEmpty()) {
                    String imageDataUrl = getImageAsDataUrl(product.getImagePath());
                    if (imageDataUrl != null && !imageDataUrl.isEmpty()) {
                        log.info("Product image data URL generated for product {} in listing, length: {}", product.getId(), imageDataUrl.length());
                        html.append("<img src=\"").append(imageDataUrl).append("\" alt=\"").append(escapeHtml(product.getName())).append("\" />");
                    } else {
                        log.warn("Image data URL generation failed for product {} in listing with path: {}", product.getId(), product.getImagePath());
                    }
                }
                
                html.append("<div class=\"product-info\">");
                html.append("<h3>").append(escapeHtml(product.getName())).append("</h3>");
                if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                    html.append("<p>").append(escapeHtml(product.getDescription())).append("</p>");
                }
                
                // Price and quantity in a clean layout
                html.append("<div class=\"product-price-qty\">");
                // Convert from smallest unit to DOGE for display (1 DOGE = 100,000,000 smallest units)
                double priceInDoge = product.getPriceDoge() / 100000000.0;
                html.append("<div class=\"price\">").append(String.format("%.8f", priceInDoge).replaceAll("0+$", "").replaceAll("\\.$", "")).append(" DOGE</div>");
                // Show stock quantity (or "Unlimited" if -1)
                if (product.getQuantity() == -1) {
                    html.append("<div class=\"quantity\">Stock: Unlimited</div>");
                } else {
                    html.append("<div class=\"quantity\">Stock: ").append(product.getQuantity()).append("</div>");
                }
                html.append("</div>");
                
                // Quantity selector and Pay button
                html.append("<div class=\"product-quantity-pay\">");
                html.append("<div class=\"product-quantity-selector\">");
                html.append("<div class=\"quantity-controls\">");
                html.append("<button type=\"button\" onclick=\"decreaseQty(").append(product.getId()).append(")\" class=\"qty-btn qty-minus\">-</button>");
                int maxQty = product.getQuantity() == -1 ? 999999 : product.getQuantity();
                html.append("<input type=\"number\" id=\"qty_").append(product.getId()).append("\" min=\"1\" max=\"").append(maxQty).append("\" value=\"1\" class=\"quantity-input\" />");
                html.append("<button type=\"button\" onclick=\"increaseQty(").append(product.getId()).append(")\" class=\"qty-btn qty-plus\">+</button>");
                html.append("</div>");
                html.append("</div>");
                html.append("<button onclick=\"payProduct(").append(product.getId()).append(")\" class=\"btn-pay-doge\">Pay In Doge</button>");
                html.append("</div>");
                
                html.append("</div>"); // product-info
                html.append("</div>"); // product-card
            }
            
            html.append("<script>");
            html.append("function increaseQty(productId) {");
            html.append("  var qtyInput = document.getElementById('qty_' + productId);");
            html.append("  if (qtyInput) {");
            html.append("    var current = parseInt(qtyInput.value) || 1;");
            html.append("    var max = parseInt(qtyInput.max) || 999999;");
            html.append("    if (current < max) qtyInput.value = current + 1;");
            html.append("  }");
            html.append("}");
            html.append("function decreaseQty(productId) {");
            html.append("  var qtyInput = document.getElementById('qty_' + productId);");
            html.append("  if (qtyInput) {");
            html.append("    var current = parseInt(qtyInput.value) || 1;");
            html.append("    var min = parseInt(qtyInput.min) || 1;");
            html.append("    if (current > min) qtyInput.value = current - 1;");
            html.append("  }");
            html.append("}");
            html.append("function payProduct(productId) {");
            html.append("  var qtyInput = document.getElementById('qty_' + productId);");
            html.append("  var qty = qtyInput ? qtyInput.value : 1;");
            html.append("  if (qty < 1) qty = 1;");
            html.append("  window.location.href = '/pay/' + productId + '?quantity=' + qty;");
            html.append("}");
            html.append("</script>");
            
            html.append("</div>");
            html.append("</div>");
            html.append(getHtmlFooter());
            html.append("</body></html>");
            
            return html.toString();
        } catch (NumberFormatException e) {
            return "HTTP/1.1 400 Bad Request\r\nContent-Type: text/html\r\n\r\nInvalid category ID";
        }
    }
    
    private String handlePaymentPage(String productIdStr, String quantityStr) {
        try {
            long productId = Long.parseLong(productIdStr);
            Product product = database.productDao().getProductById(productId);
            if (product == null || !product.isAvailable()) {
                return "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\n\r\n404 Product Not Found or Out of Stock";
            }
            
            int quantity = 1;
            if (quantityStr != null && !quantityStr.isEmpty()) {
                try {
                    quantity = Integer.parseInt(quantityStr);
                    if (quantity < 1) quantity = 1;
                    // If product has unlimited quantity (-1), allow any quantity
                    if (product.getQuantity() != -1 && quantity > product.getQuantity()) {
                        quantity = product.getQuantity();
                    }
                } catch (NumberFormatException e) {
                    quantity = 1;
                }
            }
            
            long totalPrice = product.getPriceDoge() * quantity;
            String paymentAddress = generatePaymentAddress(product, quantity);
            
            StringBuilder html = new StringBuilder();
            html.append("HTTP/1.1 200 OK\r\n");
            html.append("Content-Type: text/html; charset=utf-8\r\n\r\n");
            html.append(getHtmlHeader("Payment: " + product.getName()));
            html.append("<body>");
            html.append("<div class=\"container payment-page\">");
            
            html.append("<div class=\"payment-content\">");
            
            // Header with Back button and title
            html.append("<div class=\"payment-header\">");
            html.append("<a href=\"/category/").append(product.getCategoryId()).append("\" class=\"back-button-payment\">← Back</a>");
            html.append("<h1>").append(escapeHtml(product.getName())).append("</h1>");
            html.append("</div>");
            
            // Payment section
            html.append("<div id=\"payment-section\" class=\"payment-section-simple\">");
            html.append("<div id=\"qr-code-container\" class=\"qr-container-large\">");
            String qrDataUrl = getQrCodeAsDataUrl(paymentAddress, totalPrice);
            if (qrDataUrl != null && !qrDataUrl.isEmpty()) {
                log.info("QR Code data URL generated, length: {}", qrDataUrl.length());
                html.append("<img src=\"").append(qrDataUrl).append("\" alt=\"QR Code\" id=\"qr-code\" />");
            } else {
                log.warn("QR Code generation returned null for address: {}, amount: {}", paymentAddress, totalPrice);
                html.append("<div class=\"qr-error\">QR Code generation failed. Please try refreshing the page.</div>");
            }
            html.append("</div>"); // qr-container-large
            
            // Quantity and amount side by side below QR
            html.append("<div class=\"payment-details-below-qr\">");
            html.append("<div class=\"payment-detail-item-inline\">");
            html.append("<span class=\"detail-label\">Quantity:</span>");
            html.append("<span class=\"detail-value\">").append(quantity).append("</span>");
            html.append("</div>");
            html.append("<div class=\"payment-detail-item-inline\">");
            html.append("<span class=\"detail-label\">Amount to Pay:</span>");
            // Convert from smallest unit to DOGE for display (1 DOGE = 100,000,000 smallest units)
            double totalPriceInDoge = totalPrice / 100000000.0;
            html.append("<span class=\"detail-value total-amount\">").append(String.format("%.8f", totalPriceInDoge).replaceAll("0+$", "").replaceAll("\\.$", "")).append(" DOGE</span>");
            html.append("</div>");
            html.append("</div>");
            
            // Payment address below QR (without label)
            html.append("<div class=\"payment-address-simple\">");
            html.append("<code id=\"payment-address-code\">").append(paymentAddress).append("</code>");
            html.append("<button id=\"btn-copy-address\" onclick=\"copyAddress()\" class=\"btn-copy\">Copy Address</button>");
            html.append("</div>");
            html.append("</div>"); // payment-section-simple
            
            // Success message
            html.append("<div id=\"payment-success\" class=\"payment-success\" style=\"display:none;\">");
            html.append("<div class=\"success-icon\">✓</div>");
            html.append("<h2 class=\"success-title\">Payment Successful!</h2>");
            html.append("<p class=\"success-message\">Thank you for your purchase. Your payment has been received and confirmed.</p>");
            html.append("</div>");
            
            // Doge Meme Words Animation Container
            html.append("<div id=\"dogeWordsContainer\" class=\"doge-words-container\"></div>");
            
            html.append("</div>"); // payment-content
            html.append("</div>"); // payment-page
            
            html.append("<script>");
            html.append("var paymentAddress = '").append(paymentAddress).append("';");
            html.append("function copyAddress() {");
            html.append("  var btn = document.getElementById('btn-copy-address');");
            html.append("  var originalText = btn.textContent;");
            html.append("  if (navigator.clipboard && navigator.clipboard.writeText) {");
            html.append("    navigator.clipboard.writeText(paymentAddress).then(() => {");
            html.append("      showCopiedState(btn, originalText);");
            html.append("    }).catch(() => {");
            html.append("      fallbackCopyAddress(btn, originalText);");
            html.append("    });");
            html.append("  } else {");
            html.append("    fallbackCopyAddress(btn, originalText);");
            html.append("  }");
            html.append("}");
            html.append("function showCopiedState(btn, originalText) {");
            html.append("  btn.textContent = 'Copied!';");
            html.append("  btn.classList.add('copied');");
            html.append("  setTimeout(() => {");
            html.append("    btn.textContent = originalText;");
            html.append("    btn.classList.remove('copied');");
            html.append("  }, 2000);");
            html.append("}");
            html.append("function fallbackCopyAddress(btn, originalText) {");
            html.append("  var textarea = document.createElement('textarea');");
            html.append("  textarea.value = paymentAddress;");
            html.append("  textarea.style.position = 'fixed';");
            html.append("  textarea.style.left = '-999999px';");
            html.append("  textarea.style.top = '-999999px';");
            html.append("  document.body.appendChild(textarea);");
            html.append("  textarea.focus();");
            html.append("  textarea.select();");
            html.append("  try {");
            html.append("    var successful = document.execCommand('copy');");
            html.append("    if (successful) {");
            html.append("      showCopiedState(btn, originalText);");
            html.append("    } else {");
            html.append("      alert('Failed to copy address. Please copy manually: ' + paymentAddress);");
            html.append("    }");
            html.append("  } catch (err) {");
            html.append("    alert('Failed to copy address. Please copy manually: ' + paymentAddress);");
            html.append("  }");
            html.append("  document.body.removeChild(textarea);");
            html.append("}");
            html.append("var paymentCheckInterval;");
            html.append("function checkPayment() {");
            html.append("  fetch('/api/payment-status?address=' + encodeURIComponent(paymentAddress))");
            html.append("    .then(response => response.json())");
            html.append("    .then(data => {");
            html.append("      if (data.paid) {");
            html.append("        clearInterval(paymentCheckInterval);");
            html.append("        document.getElementById('payment-section').style.display = 'none';");
            html.append("        document.getElementById('payment-success').style.display = 'block';");
            html.append("        startDogeWordsAnimation();");
            html.append("        setTimeout(() => { window.location.href = '/'; }, 5000);");
            html.append("      }");
            html.append("    })");
            html.append("    .catch(error => {");
            html.append("      console.error('Error checking payment:', error);");
            html.append("    });");
            html.append("}");
            html.append("paymentCheckInterval = setInterval(checkPayment, 3000);");
            html.append("checkPayment();");
            html.append("function createDogeWord() {");
            html.append("  const container = document.getElementById('dogeWordsContainer');");
            html.append("  if (!container) return;");
            html.append("  const dogeWords = ['Much Wow!', 'Such Wallet!', 'So OpenSource!', 'Pawsome!', 'Very Secure!', 'Much Fast!', 'Such Features!', 'So Amazing!', 'Very Cool!', 'Much Safe!', 'Such Crypto!', 'So Digital!', 'Very Modern!', 'Much Smart!', 'Such Tech!', 'So Innovative!', 'Very Fun!', 'Much Wow!', 'Such Coins!', 'So Blockchain!', 'Very Private!', 'Much Control!', 'Such Freedom!', 'So Decentralized!', 'Very Wow!', 'Much Family!', 'Such Kids!', 'So Safe!', 'Very Easy!', 'Much Simple!', 'Such Sign!', 'So Digital!', 'Very Verify!', 'Much Trust!', 'Such Secure!', 'So Recurring!', 'Very Schedule!', 'Much Auto!', 'Such Smart!', 'So Advanced!', 'Very Network!', 'Much Nodes!', 'Such Connect!', 'So Global!', 'Very Worldwide!', 'Much Radio!', 'Such Offline!', 'So Signal!', 'Very Transmit!', 'Much Broadcast!', 'Such Meme!', 'So Fun!', 'Very Happy!', 'Much Joy!', 'Such Laugh!'];");
            html.append("  const x = Math.random() * (window.innerWidth - 200);");
            html.append("  const y = Math.random() * (window.innerHeight - 100);");
            html.append("  const word = dogeWords[Math.floor(Math.random() * dogeWords.length)];");
            html.append("  const wordElement = document.createElement('div');");
            html.append("  wordElement.className = 'doge-word';");
            html.append("  wordElement.textContent = word;");
            html.append("  const skews = ['skew-left', 'skew-right', 'no-skew'];");
            html.append("  const skew = skews[Math.floor(Math.random() * skews.length)];");
            html.append("  wordElement.classList.add(skew);");
            html.append("  const colors = ['color-yellow', 'color-orange', 'color-amber', 'color-white', 'color-green', 'color-blue', 'color-purple', 'color-pink'];");
            html.append("  const color = colors[Math.floor(Math.random() * colors.length)];");
            html.append("  wordElement.classList.add(color);");
            html.append("  const rotation = Math.random() * 30 - 15;");
            html.append("  wordElement.style.setProperty('--rotation', rotation + 'deg');");
            html.append("  wordElement.style.left = x + 'px';");
            html.append("  wordElement.style.top = y + 'px';");
            html.append("  container.appendChild(wordElement);");
            html.append("  setTimeout(() => { if (wordElement.parentNode) { wordElement.parentNode.removeChild(wordElement); } }, 6900);");
            html.append("}");
            html.append("function startDogeWordsAnimation() {");
            html.append("  const wordCount = 15;");
            html.append("  const duration = 3000;");
            html.append("  for (let i = 0; i < wordCount; i++) {");
            html.append("    setTimeout(() => { createDogeWord(); }, i * (duration / wordCount));");
            html.append("  }");
            html.append("}");
            html.append("setInterval(checkPayment, 3000);");
            html.append("</script>");
            
            html.append(getHtmlFooter());
            html.append("</body></html>");
            
            return html.toString();
        } catch (NumberFormatException e) {
            return "HTTP/1.1 400 Bad Request\r\nContent-Type: text/html\r\n\r\nInvalid product ID";
        }
    }
    
    private String handleProductPage(String productIdStr, String quantityStr) {
        try {
            long productId = Long.parseLong(productIdStr);
            Product product = database.productDao().getProductById(productId);
            if (product == null || !product.isAvailable()) {
                return "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\n\r\n404 Product Not Found or Out of Stock";
            }
            
            int quantity = 1;
            if (quantityStr != null && !quantityStr.isEmpty()) {
                try {
                    quantity = Integer.parseInt(quantityStr);
                    if (quantity < 1) quantity = 1;
                    // If product has unlimited quantity (-1), allow any quantity
                    if (product.getQuantity() != -1 && quantity > product.getQuantity()) {
                        quantity = product.getQuantity();
                    }
                } catch (NumberFormatException e) {
                    quantity = 1;
                }
            }
            
            Category category = database.categoryDao().getCategoryById(product.getCategoryId());
            long totalPrice = product.getPriceDoge() * quantity;
            
            // Generate unique payment address for this product/quantity
            String paymentAddress = generatePaymentAddress(product, quantity);
            
            StringBuilder html = new StringBuilder();
            html.append("HTTP/1.1 200 OK\r\n");
            html.append("Content-Type: text/html; charset=utf-8\r\n\r\n");
            html.append(getHtmlHeader("Product: " + product.getName()));
            html.append("<body>");
            html.append("<div class=\"container product-page\">");
            html.append("<a href=\"/category/").append(category.getId()).append("\" class=\"back-link\">← Back to ").append(escapeHtml(category.getName())).append("</a>");
            
            html.append("<div class=\"product-page-content\">");
            html.append("<h1>").append(escapeHtml(product.getName())).append("</h1>");
            
            // Main content: Product image, details, and QR code side by side
            html.append("<div class=\"product-main-content\">");
            
            // Left side: Product image and description
            html.append("<div class=\"product-left-section\">");
            if (product.getImagePath() != null && !product.getImagePath().isEmpty()) {
                String imageDataUrl = getImageAsDataUrl(product.getImagePath());
                if (imageDataUrl != null && !imageDataUrl.isEmpty()) {
                    log.info("Product image data URL generated for product {}, length: {}", product.getId(), imageDataUrl.length());
                    html.append("<img src=\"").append(imageDataUrl).append("\" class=\"product-image\" alt=\"").append(escapeHtml(product.getName())).append("\" />");
                } else {
                    log.warn("Image data URL generation failed for product {} with path: {}", product.getId(), product.getImagePath());
                }
            }
            if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                html.append("<p class=\"description\">").append(escapeHtml(product.getDescription())).append("</p>");
            }
            html.append("</div>"); // product-left-section
            
            // Right side: Product details and payment
            html.append("<div class=\"product-right-section\">");
            
            // Product details
            html.append("<div class=\"product-details\">");
            // Convert from smallest unit to DOGE for display (1 DOGE = 100,000,000 smallest units)
            double priceInDoge = product.getPriceDoge() / 100000000.0;
            html.append("<div class=\"price\">Price: ").append(String.format("%.8f", priceInDoge).replaceAll("0+$", "").replaceAll("\\.$", "")).append(" DOGE each</div>");
            // Show stock quantity (or "Unlimited" if -1)
            if (product.getQuantity() == -1) {
                html.append("<div class=\"quantity-info\">In Stock: Unlimited</div>");
            } else {
                html.append("<div class=\"quantity-info\">In Stock: ").append(product.getQuantity()).append("</div>");
            }
            
            html.append("<div class=\"quantity-selector\">");
            html.append("<label>Quantity:</label>");
            int maxQty = product.getQuantity() == -1 ? 999999 : product.getQuantity();
            html.append("<input type=\"number\" id=\"quantity\" min=\"1\" max=\"").append(maxQty).append("\" value=\"").append(quantity).append("\" />");
            html.append("<button onclick=\"updateQuantity()\">Update</button>");
            html.append("</div>");
            
            // Convert from smallest unit to DOGE for display (1 DOGE = 100,000,000 smallest units)
            double totalPriceInDoge = totalPrice / 100000000.0;
            html.append("<div class=\"total-price\">Total: <strong>").append(String.format("%.8f", totalPriceInDoge).replaceAll("0+$", "").replaceAll("\\.$", "")).append(" DOGE</strong></div>");
            html.append("</div>"); // product-details
            
            // Payment section with QR code
            html.append("<div id=\"payment-section\" class=\"payment-section\">");
            html.append("<h2>Payment</h2>");
            
            html.append("<div class=\"payment-content-wrapper\">");
            // QR code - larger and prominent
            html.append("<div id=\"qr-code-container\" class=\"qr-container\">");
            String qrDataUrl = getQrCodeAsDataUrl(paymentAddress, totalPrice);
            if (qrDataUrl != null && !qrDataUrl.isEmpty()) {
                log.info("QR Code data URL generated, length: {}", qrDataUrl.length());
                html.append("<img src=\"").append(qrDataUrl).append("\" alt=\"QR Code\" id=\"qr-code\" />");
            } else {
                log.warn("QR Code generation returned null for address: {}, amount: {}", paymentAddress, totalPrice);
                html.append("<div class=\"qr-error\">QR Code generation failed. Please try refreshing the page.</div>");
            }
            html.append("</div>"); // qr-container
            
            // Payment address below QR
            html.append("<div class=\"payment-address\">");
            html.append("<label>Payment Address:</label>");
            html.append("<code>").append(paymentAddress).append("</code>");
            html.append("<button id=\"btn-copy-address\" onclick=\"copyAddress()\">Copy</button>");
            html.append("</div>");
            
            // Reuse totalPriceInDoge already calculated above
            html.append("<div class=\"payment-amount\">Amount: <strong>").append(String.format("%.8f", totalPriceInDoge).replaceAll("0+$", "").replaceAll("\\.$", "")).append(" DOGE</strong></div>");
            html.append("<p class=\"payment-info\">Scan the QR code or copy the address to send payment</p>");
            html.append("</div>"); // payment-content-wrapper
            html.append("</div>"); // payment-section
            
            html.append("</div>"); // product-right-section
            html.append("</div>"); // product-main-content
            
            html.append("<div id=\"payment-success\" class=\"payment-success\" style=\"display:none;\">");
            html.append("<h2>✓ Payment Successful!</h2>");
            html.append("<p>Thank you for your purchase!</p>");
            html.append("</div>");
            
            html.append("</div>"); // product-page-content
            html.append("</div>"); // product-page
            
            html.append("<script>");
            html.append("var productId = ").append(productId).append(";");
            html.append("var paymentAddress = '").append(paymentAddress).append("';");
            html.append("function updateQuantity() {");
            html.append("  var qty = document.getElementById('quantity').value;");
            html.append("  window.location.href = '/product/").append(productId).append("?quantity=' + qty;");
            html.append("}");
            html.append("function copyAddress() {");
            html.append("  var btn = document.getElementById('btn-copy-address');");
            html.append("  var originalText = btn.textContent;");
            html.append("  if (navigator.clipboard && navigator.clipboard.writeText) {");
            html.append("    navigator.clipboard.writeText(paymentAddress).then(() => {");
            html.append("      showCopiedState(btn, originalText);");
            html.append("    }).catch(() => {");
            html.append("      fallbackCopyAddress(btn, originalText);");
            html.append("    });");
            html.append("  } else {");
            html.append("    fallbackCopyAddress(btn, originalText);");
            html.append("  }");
            html.append("}");
            html.append("function showCopiedState(btn, originalText) {");
            html.append("  btn.textContent = 'Copied!';");
            html.append("  btn.classList.add('copied');");
            html.append("  setTimeout(() => {");
            html.append("    btn.textContent = originalText;");
            html.append("    btn.classList.remove('copied');");
            html.append("  }, 2000);");
            html.append("}");
            html.append("function fallbackCopyAddress(btn, originalText) {");
            html.append("  var textarea = document.createElement('textarea');");
            html.append("  textarea.value = paymentAddress;");
            html.append("  textarea.style.position = 'fixed';");
            html.append("  textarea.style.left = '-999999px';");
            html.append("  textarea.style.top = '-999999px';");
            html.append("  document.body.appendChild(textarea);");
            html.append("  textarea.focus();");
            html.append("  textarea.select();");
            html.append("  try {");
            html.append("    var successful = document.execCommand('copy');");
            html.append("    if (successful) {");
            html.append("      showCopiedState(btn, originalText);");
            html.append("    } else {");
            html.append("      alert('Failed to copy address. Please copy manually: ' + paymentAddress);");
            html.append("    }");
            html.append("  } catch (err) {");
            html.append("    alert('Failed to copy address. Please copy manually: ' + paymentAddress);");
            html.append("  }");
            html.append("  document.body.removeChild(textarea);");
            html.append("}");
            html.append("var paymentCheckInterval;");
            html.append("function checkPayment() {");
            html.append("  fetch('/api/payment-status?address=' + encodeURIComponent(paymentAddress))");
            html.append("    .then(r => r.json())");
            html.append("    .then(data => {");
            html.append("      if (data.paid) {");
            html.append("        clearInterval(paymentCheckInterval);");
            html.append("        document.getElementById('payment-section').style.display = 'none';");
            html.append("        document.getElementById('payment-success').style.display = 'block';");
            html.append("      }");
            html.append("    })");
            html.append("    .catch(error => {");
            html.append("      console.error('Error checking payment:', error);");
            html.append("    });");
            html.append("}");
            html.append("paymentCheckInterval = setInterval(checkPayment, 3000);");
            html.append("checkPayment();");
            html.append("</script>");
            
            html.append(getHtmlFooter());
            html.append("</body></html>");
            
            return html.toString();
        } catch (NumberFormatException e) {
            return "HTTP/1.1 400 Bad Request\r\nContent-Type: text/html\r\n\r\nInvalid product ID";
        }
    }
    
    private String handleApiRequest(String path, String method, java.io.BufferedReader reader) throws IOException {
        if (path.startsWith("/api/categories")) {
            List<Category> categories = database.categoryDao().getAllCategories();
            return createJsonResponse(gson.toJson(categories));
        } else if (path.startsWith("/api/products")) {
            Map<String, String> params = parseQueryString(path);
            String categoryId = params.get("categoryId");
            List<Product> products;
            if (categoryId != null) {
                products = database.productDao().getAvailableProductsByCategory(Long.parseLong(categoryId));
            } else {
                products = database.productDao().getAllProducts();
            }
            return createJsonResponse(gson.toJson(products));
        } else if (path.startsWith("/api/payment-status")) {
            Map<String, String> params = parseQueryString(path);
            String address = params.get("address");
            PaymentInfo info = pendingPayments.get(address);
            Map<String, Object> response = new HashMap<>();
            response.put("paid", info != null && info.isPaid);
            return createJsonResponse(gson.toJson(response));
        }
        
        return "HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\n\r\n{\"error\":\"Not Found\"}";
    }
    
    
    private String generatePaymentAddress(Product product, int quantity) {
        // Check if we already have a payment address for this product/quantity
        String existingAddress = product.getPaymentAddress();
        if (existingAddress != null && product.getRequestedQuantity() == quantity) {
            // Reuse existing address
            pendingPayments.put(existingAddress, new PaymentInfo(product.getId(), quantity, product.getPriceDoge() * quantity));
            return existingAddress;
        }
        
        // Generate new address
        final Address[] addressResult = new Address[1];
        final Exception[] exceptionResult = new Exception[1];
        
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                if (wallet != null) {
                    addressResult[0] = wallet.freshReceiveAddress();
                }
            } catch (Exception e) {
                exceptionResult[0] = e;
            }
        });
        
        // Wait for address generation
        long startTime = System.currentTimeMillis();
        while (addressResult[0] == null && exceptionResult[0] == null && 
               (System.currentTimeMillis() - startTime) < 5000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (addressResult[0] != null) {
            String address = addressResult[0].toString();
            
            // Save to product
            product.setPaymentAddress(address);
            product.setRequestedQuantity(quantity);
            database.productDao().updateProduct(product);
            
            // Track payment
            pendingPayments.put(address, new PaymentInfo(product.getId(), quantity, product.getPriceDoge() * quantity));
            
            return address;
        }
        
        return null;
    }
    
    private void handlePayment(Transaction tx) {
        for (TransactionOutput output : tx.getOutputs()) {
            try {
                Address address = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                PaymentInfo paymentInfo = pendingPayments.get(address.toString());
                
                if (paymentInfo != null && !paymentInfo.isPaid) {
                    Coin receivedAmount = output.getValue();
                    Coin expectedAmount = Coin.valueOf(paymentInfo.totalPriceDoge);
                    
                    // Check if payment amount matches (with small tolerance)
                    if (receivedAmount.compareTo(expectedAmount) >= 0) {
                        paymentInfo.isPaid = true;
                        
                        // Deduct quantity (only if not unlimited)
                        Product product = database.productDao().getProductById(paymentInfo.productId);
                        if (product != null) {
                            // Only deduct quantity if product is not unlimited (-1)
                            if (product.getQuantity() != -1) {
                                int newQuantity = product.getQuantity() - paymentInfo.quantity;
                                if (newQuantity < 0) newQuantity = 0;
                                product.setQuantity(newQuantity);
                            }
                            // Unlimited products stay unlimited (-1)
                            
                            product.setPaymentAddress(null); // Clear payment address
                            product.setRequestedQuantity(0);
                            database.productDao().updateProduct(product);
                            
                            log.info("Payment received for product {}: {} DOGE", product.getName(), receivedAmount.toFriendlyString());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing payment", e);
            }
        }
    }
    
    private String getHtmlHeader(String title) {
        return "<!DOCTYPE html><html><head>" +
                "<meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<title>" + escapeHtml(title) + "</title>" +
                "<link href='https://fonts.googleapis.com/css2?family=Comic+Neue:wght@300;400;500;600;700&display=swap' rel='stylesheet'>" +
                "<style>" + getCss() + "</style>" +
                "</head>";
    }
    
    private String getHtmlFooter() {
        return "";
    }
    
    private String getImageAsDataUrl(String imagePath) {
        try {
            if (imagePath == null || imagePath.isEmpty()) {
                log.warn("Image path is null or empty");
                return null;
            }
            
            java.io.File imageFile = new java.io.File(imagePath);
            if (!imageFile.exists()) {
                log.warn("Image file does not exist: {}", imagePath);
                return null;
            }
            
            if (!imageFile.canRead()) {
                log.warn("Image file cannot be read: {}", imagePath);
                return null;
            }
            
            log.info("Reading image file: {}, size: {} bytes", imagePath, imageFile.length());
            
            // Determine image type from file extension
            String mimeType = "image/jpeg";
            String fileName = imageFile.getName().toLowerCase();
            if (fileName.endsWith(".png")) {
                mimeType = "image/png";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            }
            
            InputStream is = new java.io.FileInputStream(imageFile);
            byte[] buffer = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] imageBytes = baos.toByteArray();
            is.close();
            
            log.info("Image file read, {} bytes", imageBytes.length);
            
            if (imageBytes.length == 0) {
                log.warn("Image file is empty: {}", imagePath);
                return null;
            }
            
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            String dataUrl = "data:" + mimeType + ";base64," + base64;
            log.info("Image data URL generated, total length: {}", dataUrl.length());
            
            return dataUrl;
        } catch (Exception e) {
            log.error("Error reading image file: {}", imagePath, e);
        }
        return null;
    }
    
    private String getQrCodeAsDataUrl(String address, long amount) {
        try {
            if (address == null || address.isEmpty()) {
                log.warn("Cannot generate QR code: address is null or empty");
                return null;
            }
            
            String uri = "dogecoin:" + address + "?amount=" + amount;
            log.info("Generating QR code for URI: {}", uri);
            
            Bitmap qrBitmap = Qr.bitmap(uri);
            if (qrBitmap == null) {
                log.warn("Qr.bitmap returned null for URI: {}", uri);
                return null;
            }
            
            log.info("QR bitmap created: {}x{}, config: {}", qrBitmap.getWidth(), qrBitmap.getHeight(), qrBitmap.getConfig());
            
            // Convert ALPHA_8 bitmap to RGB for proper web display
            Bitmap rgbBitmap = qrBitmap;
            if (qrBitmap.getConfig() == Bitmap.Config.ALPHA_8) {
                log.info("Converting ALPHA_8 bitmap to RGB");
                rgbBitmap = convertAlpha8ToRgb(qrBitmap);
                if (rgbBitmap == null) {
                    log.warn("convertAlpha8ToRgb returned null");
                    return null;
                }
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean compressed = rgbBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            if (!compressed) {
                log.warn("Failed to compress QR bitmap to PNG");
                return null;
            }
            
            byte[] imageBytes = baos.toByteArray();
            log.info("QR code PNG compressed, size: {} bytes", imageBytes.length);
            
            if (imageBytes.length == 0) {
                log.warn("Compressed QR code is empty");
                return null;
            }
            
            String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            String dataUrl = "data:image/png;base64," + base64;
            log.info("QR code data URL generated, total length: {}", dataUrl.length());
            
            return dataUrl;
        } catch (Exception e) {
            log.error("Error generating QR code for address: {}, amount: {}", address, amount, e);
        }
        return null;
    }
    
    private Bitmap convertAlpha8ToRgb(Bitmap alphaBitmap) {
        // Convert ALPHA_8 bitmap (grayscale) to RGB with white background and black QR code
        int width = alphaBitmap.getWidth();
        int height = alphaBitmap.getHeight();
        
        // Create RGB bitmap with white background
        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        
        // Read pixels from ALPHA_8 bitmap as bytes
        byte[] alphaPixels = new byte[width * height];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(alphaPixels);
        alphaBitmap.copyPixelsToBuffer(buffer);
        
        // Convert to RGB pixels
        int[] rgbPixels = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            byte pixel = alphaPixels[i];
            // In ALPHA_8, -1 (0xFF) means opaque/black QR code, 0 means transparent/white background
            if ((pixel & 0xFF) > 128) {
                rgbPixels[i] = 0xFF000000; // Black QR code
            } else {
                rgbPixels[i] = 0xFFFFFFFF; // White background
            }
        }
        
        rgbBitmap.setPixels(rgbPixels, 0, width, 0, 0, width, height);
        return rgbBitmap;
    }
    
    private String getCss() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; } " +
                "html { height: 100%; } " +
                "body { font-family: 'Comic Neue', 'Arial', sans-serif; margin: 0; padding: 0; background: #0a0a0a; color: #ffffff; line-height: 1.6; min-height: 100vh; } " +
                ".container { max-width: 1400px; margin: 0 auto; padding: 15px; width: 100%; } " +
                ".product-page { display: flex; flex-direction: column; height: 100vh; overflow: hidden; padding: 10px; } " +
                ".product-page > .back-link { flex-shrink: 0; margin-bottom: 10px; } " +
                ".product-page-content { flex: 1; display: flex; flex-direction: column; overflow: hidden; gap: 15px; } " +
                "h1 { color: #ffc107; font-size: clamp(1.5em, 3vw, 2.2em); margin-bottom: 15px; text-align: center; text-shadow: 0 0 20px rgba(255, 193, 7, 0.3); } " +
                "h2 { color: #ffc107; font-size: clamp(1.2em, 2.5vw, 1.8em); margin-bottom: 15px; } " +
                ".product-main-content { display: flex; gap: 20px; flex: 1; min-height: 0; } " +
                ".product-left-section { flex: 1; display: flex; flex-direction: column; gap: 15px; min-width: 0; } " +
                ".product-right-section { flex: 1; display: flex; flex-direction: column; gap: 15px; min-width: 0; } " +
                ".categories, .products { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px; margin-top: 30px; } " +
                ".category-card, .product-card { display: block; padding: 30px; background: linear-gradient(145deg, #1e1e1e, #252525); border: 1px solid #333333; border-radius: 16px; text-decoration: none; color: inherit; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4); } " +
                ".category-card:hover, .product-card:hover { transform: translateY(-5px); box-shadow: 0 12px 40px rgba(255, 193, 7, 0.2); border-color: #ffc107; } " +
                ".category-card h2 { color: #ffc107; margin: 0; font-size: 1.5em; } " +
                ".product-card img { width: 100%; height: 220px; object-fit: cover; border-radius: 12px; margin-bottom: 15px; border: 2px solid #333333; display: block; } " +
                ".product-info h3 { color: #ffffff; margin: 0 0 10px 0; font-size: 1.3em; } " +
                ".product-info p { color: #b0b0b0; margin: 10px 0; font-size: 0.95em; } " +
                ".product-price-qty { display: flex; flex-direction: column; gap: 8px; margin-top: 15px; padding: 12px; background: rgba(0, 0, 0, 0.3); border-radius: 8px; } " +
                ".price { font-size: 1.6em; font-weight: bold; color: #ffc107; margin: 0; } " +
                ".quantity { color: #b0b0b0; font-size: 1em; margin: 0; } " +
                ".back-link { display: inline-block; margin-bottom: 10px; color: #ffc107; text-decoration: none; font-size: clamp(0.9em, 2vw, 1.1em); transition: all 0.3s; padding: 8px 16px; border-radius: 8px; background: rgba(255, 193, 7, 0.1); } " +
                ".back-link:hover { background: rgba(255, 193, 7, 0.2); transform: translateX(-5px); } " +
                ".product-image { width: 100%; max-height: 400px; height: auto; object-fit: contain; border-radius: 12px; border: 2px solid #333333; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4); display: block; } " +
                ".description { color: #b0b0b0; font-size: clamp(0.95em, 2vw, 1.1em); line-height: 1.6; margin: 10px 0; flex-shrink: 0; } " +
                ".product-details { background: linear-gradient(145deg, #1e1e1e, #252525); padding: 15px; border-radius: 12px; border: 1px solid #333333; flex-shrink: 0; } " +
                ".quantity-info { color: #b0b0b0; font-size: clamp(0.9em, 2vw, 1.1em); margin: 10px 0; } " +
                ".payment-section { display: flex; flex-direction: column; padding: 20px; background: linear-gradient(145deg, #1e1e1e, #252525); border-radius: 16px; border: 2px solid #ffc107; box-shadow: 0 0 30px rgba(255, 193, 7, 0.1); flex: 1; min-height: 0; } " +
                ".payment-section h2 { color: #ffc107; margin-bottom: 15px; flex-shrink: 0; font-size: clamp(1.2em, 2.5vw, 1.8em); } " +
                ".payment-content-wrapper { flex: 1; display: flex; flex-direction: column; gap: 15px; min-height: 0; } " +
                ".qr-container { flex: 1; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(0, 0, 0, 0.3); border-radius: 12px; min-height: 200px; } " +
                ".qr-container img { min-width: 250px; min-height: 250px; max-width: 100%; max-height: 100%; width: auto; height: auto; object-fit: contain; border-radius: 12px; padding: 20px; background: #ffffff; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5); } " +
                ".payment-page { display: flex; flex-direction: column; padding: 20px; justify-content: center; align-items: center; } " +
                ".payment-content { width: 100%; max-width: 500px; display: flex; flex-direction: column; gap: 15px; } " +
                ".payment-header { display: flex; align-items: center; gap: 15px; margin-bottom: 5px; } " +
                ".back-button-payment { color: #ffc107; text-decoration: none; font-size: clamp(0.95em, 2vw, 1.1em); padding: 8px 16px; border-radius: 8px; background: rgba(255, 193, 7, 0.1); transition: all 0.3s; white-space: nowrap; } " +
                ".back-button-payment:hover { background: rgba(255, 193, 7, 0.2); transform: translateX(-3px); } " +
                ".payment-page h1 { color: #ffc107; font-size: clamp(1.6em, 3.5vw, 2.2em); text-align: left; margin: 0; flex: 1; text-shadow: 0 0 20px rgba(255, 193, 7, 0.3); } " +
                ".payment-section-simple { background: linear-gradient(145deg, #1e1e1e, #252525); padding: 25px; border-radius: 16px; border: 2px solid #ffc107; box-shadow: 0 0 30px rgba(255, 193, 7, 0.1); display: flex; flex-direction: column; gap: 15px; } " +
                ".qr-container-large { display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(0, 0, 0, 0.3); border-radius: 12px; min-height: 250px; } " +
                ".qr-container-large img { min-width: 220px; min-height: 220px; max-width: 100%; max-height: 50vh; width: auto; height: auto; object-fit: contain; border-radius: 12px; padding: 20px; background: #ffffff; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5); } " +
                ".payment-details-below-qr { display: flex; gap: 12px; justify-content: center; align-items: center; flex-wrap: wrap; padding: 0 10px; } " +
                ".payment-detail-item-inline { display: flex; flex-direction: column; gap: 5px; text-align: center; flex: 1; min-width: 140px; } " +
                ".detail-label { color: #b0b0b0; font-size: clamp(0.95em, 2vw, 1.1em); } " +
                ".detail-value { color: #ffffff; font-size: clamp(1.2em, 3vw, 1.5em); font-weight: bold; } " +
                ".total-amount { color: #ffc107; font-size: clamp(1.4em, 3.5vw, 1.9em) !important; } " +
                ".payment-address-simple { text-align: center; } " +
                ".payment-address-simple code { display: block; color: #ffffff; background: rgba(0, 0, 0, 0.5); padding: 15px; border-radius: 8px; word-break: break-all; font-family: 'Courier New', monospace; font-size: clamp(0.8em, 1.8vw, 0.95em); margin-bottom: 15px; } " +
                ".btn-copy { padding: 12px 30px; background: linear-gradient(135deg, #ffc107, #ff8f00); color: #000000; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; font-size: clamp(0.9em, 2vw, 1em); transition: all 0.3s; box-shadow: 0 4px 16px rgba(255, 193, 7, 0.3); } " +
                ".btn-copy:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(255, 193, 7, 0.4); } " +
                ".btn-copy.copied { background: linear-gradient(135deg, #4CAF50, #45a049); color: #ffffff; box-shadow: 0 4px 16px rgba(76, 175, 80, 0.3); } " +
                ".payment-success { text-align: center; padding: 60px 40px; background: linear-gradient(135deg, #4CAF50, #45a049); border-radius: 16px; box-shadow: 0 8px 32px rgba(76, 175, 80, 0.3); position: relative; z-index: 1; } " +
                ".success-icon { font-size: clamp(4em, 10vw, 6em); color: white; margin-bottom: 20px; } " +
                ".success-title { color: white; font-size: clamp(2em, 5vw, 3em); margin-bottom: 20px; font-weight: bold; } " +
                ".success-message { color: white; font-size: clamp(1.2em, 3vw, 1.6em); } " +
                ".doge-words-container { position: fixed; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 2000; overflow: hidden; } " +
                ".doge-word { position: absolute; font-family: 'Comic Neue', cursive; font-weight: 800; font-size: 2.5rem; color: #ffc107; pointer-events: none; user-select: none; animation: dogeWordPop 2s ease-out forwards; transform-origin: center; z-index: 2001; } " +
                ".doge-word.skew-left { transform: rotate(-15deg) scale(1); } " +
                ".doge-word.skew-right { transform: rotate(15deg) scale(1); } " +
                ".doge-word.no-skew { transform: rotate(0deg) scale(1); } " +
                ".doge-word.color-yellow { color: #ffc107; } " +
                ".doge-word.color-orange { color: #ff8f00; } " +
                ".doge-word.color-amber { color: #ffb300; } " +
                ".doge-word.color-white { color: #ffffff; } " +
                ".doge-word.color-green { color: #4CAF50; } " +
                ".doge-word.color-blue { color: #2196F3; } " +
                ".doge-word.color-purple { color: #9C27B0; } " +
                ".doge-word.color-pink { color: #E91E63; } " +
                "@keyframes dogeWordPop { 0% { opacity: 0; transform: scale(0.1) rotate(0deg); } 20% { opacity: 1; transform: scale(1.2) rotate(var(--rotation, 0deg)); } 40% { transform: scale(0.9) rotate(var(--rotation, 0deg)); } 60% { transform: scale(1.1) rotate(var(--rotation, 0deg)); } 80% { transform: scale(0.95) rotate(var(--rotation, 0deg)); } 100% { opacity: 0; transform: scale(1.1) rotate(var(--rotation, 0deg)) translateY(-100px); } } " +
                ".product-quantity-pay { margin-top: 20px; display: flex; flex-direction: column; gap: 15px; } " +
                ".product-quantity-selector { display: flex; flex-direction: column; gap: 8px; align-items: center; } " +
                ".product-quantity-selector label { color: #ffffff; font-size: clamp(0.9em, 2vw, 1.1em); } " +
                ".quantity-controls { display: flex; align-items: center; gap: 8px; justify-content: center; } " +
                ".qty-btn { width: 44px; height: 44px; padding: 0; background: linear-gradient(135deg, #ffc107, #ff8f00); color: #000000; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; font-size: 20px; transition: all 0.3s; box-shadow: 0 2px 8px rgba(255, 193, 7, 0.3); display: flex; align-items: center; justify-content: center; } " +
                ".qty-btn:hover { transform: scale(1.1); box-shadow: 0 4px 12px rgba(255, 193, 7, 0.4); } " +
                ".qty-btn:active { transform: scale(0.95); } " +
                ".quantity-input { width: 80px; padding: 10px; border: 2px solid #333333; border-radius: 8px; background: rgba(0, 0, 0, 0.5); color: #ffffff; font-size: clamp(0.9em, 2vw, 1.1em); font-weight: bold; text-align: center; } " +
                ".quantity-input:focus { outline: none; border-color: #ffc107; box-shadow: 0 0 10px rgba(255, 193, 7, 0.3); } " +
                ".btn-pay-doge { padding: 12px 24px; background: linear-gradient(135deg, #ffc107, #ff8f00); color: #000000; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; font-size: clamp(0.95em, 2vw, 1.1em); transition: all 0.3s; box-shadow: 0 4px 16px rgba(255, 193, 7, 0.3); width: 100%; } " +
                ".btn-pay-doge:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(255, 193, 7, 0.4); } " +
                "@media (max-width: 768px) { .payment-page { padding: 15px; } .qr-container-large img { min-width: 200px; min-height: 200px; } .payment-content { max-width: 100%; } } " +
                ".payment-address { flex-shrink: 0; padding: 15px; background: rgba(0, 0, 0, 0.3); border-radius: 12px; } " +
                ".payment-address label { display: block; color: #ffc107; margin-bottom: 8px; font-weight: bold; font-size: clamp(0.9em, 2vw, 1.1em); } " +
                ".payment-address code { display: block; color: #ffffff; background: rgba(0, 0, 0, 0.5); padding: 10px; border-radius: 8px; word-break: break-all; font-family: 'Courier New', monospace; font-size: clamp(0.75em, 1.5vw, 0.9em); margin-bottom: 10px; overflow-x: auto; } " +
                ".payment-address button, .quantity-selector button { padding: 10px 20px; background: linear-gradient(135deg, #ffc107, #ff8f00); color: #000000; border: none; border-radius: 8px; cursor: pointer; font-weight: bold; font-size: clamp(0.85em, 2vw, 1em); transition: all 0.3s; box-shadow: 0 4px 16px rgba(255, 193, 7, 0.3); } " +
                ".payment-address button:hover, .quantity-selector button:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(255, 193, 7, 0.4); } " +
                ".payment-address button.copied, #btn-copy-address.copied { background: linear-gradient(135deg, #4CAF50, #45a049); color: #ffffff; box-shadow: 0 4px 16px rgba(76, 175, 80, 0.3); } " +
                ".payment-amount { font-size: clamp(1.3em, 3vw, 1.8em); margin: 10px 0; color: #ffc107; text-align: center; flex-shrink: 0; } " +
                ".payment-amount strong { font-size: 1.2em; } " +
                ".payment-info { color: #b0b0b0; text-align: center; margin-top: 10px; font-size: clamp(0.8em, 1.5vw, 0.95em); flex-shrink: 0; } " +
                ".payment-success { text-align: center; padding: 40px 30px; background: linear-gradient(135deg, #4CAF50, #45a049); color: white; border-radius: 16px; box-shadow: 0 8px 32px rgba(76, 175, 80, 0.3); position: relative; z-index: 1; } " +
                ".payment-success h2 { color: white; font-size: clamp(1.8em, 4vw, 2.5em); margin-bottom: 15px; } " +
                ".payment-success p { font-size: clamp(1em, 2.5vw, 1.3em); } " +
                ".quantity-selector { margin: 15px 0; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; flex-shrink: 0; } " +
                ".quantity-selector label { color: #ffffff; font-weight: bold; font-size: clamp(0.9em, 2vw, 1.1em); } " +
                ".quantity-selector input { width: 80px; padding: 10px; border: 2px solid #333333; border-radius: 8px; background: rgba(0, 0, 0, 0.5); color: #ffffff; font-size: clamp(0.9em, 2vw, 1.1em); font-weight: bold; } " +
                ".quantity-selector input:focus { outline: none; border-color: #ffc107; box-shadow: 0 0 10px rgba(255, 193, 7, 0.3); } " +
                ".total-price { font-size: clamp(1.5em, 3.5vw, 2em); margin: 15px 0; color: #ffc107; text-align: center; font-weight: bold; flex-shrink: 0; } " +
                ".total-price strong { font-size: 1.2em; } " +
                ".qr-error { color: #ff6b6b; padding: 20px; text-align: center; background: rgba(255, 107, 107, 0.1); border-radius: 8px; } " +
                "@media (max-width: 768px) { .product-main-content { flex-direction: column; } .product-image { max-height: 300px; } .qr-container img { min-width: 200px; min-height: 200px; } } ";
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private String createJsonResponse(String json) {
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: application/json; charset=utf-8\r\n" +
               "Access-Control-Allow-Origin: *\r\n\r\n" +
               json;
    }
    
    private Map<String, String> parseQueryString(String path) {
        Map<String, String> params = new HashMap<>();
        if (path.contains("?")) {
            String query = path.substring(path.indexOf("?") + 1);
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    try {
                        params.put(keyValue[0], URLDecoder.decode(keyValue[1], "UTF-8"));
                    } catch (Exception e) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                }
            }
        }
        return params;
    }
    
    private static class PaymentInfo {
        long productId;
        int quantity;
        long totalPriceDoge;
        boolean isPaid = false;
        
        PaymentInfo(long productId, int quantity, long totalPriceDoge) {
            this.productId = productId;
            this.quantity = quantity;
            this.totalPriceDoge = totalPriceDoge;
        }
    }
}

