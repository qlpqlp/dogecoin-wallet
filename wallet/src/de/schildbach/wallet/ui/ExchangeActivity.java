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

package de.schildbach.wallet.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Activity for buying and selling Dogecoin through Metal Pay Connect
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class ExchangeActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(ExchangeActivity.class);
    
    private WalletApplication application;
    private Configuration config;
    
    private CardView cardBuyDoge;
    private CardView cardSellDoge;
    private WebView webViewExchange;
    private ProgressBar progressBar;
    
    private String exchangeType = null; // "buy" or "sell"
    
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        application = getWalletApplication();
        config = application.getConfiguration();
        
        // Check if Exchange is enabled in Labs
        if (!config.getLabsExchangeEnabled()) {
            Toast.makeText(this, "Exchange is disabled. Please enable it in Settings > Configuration > Labs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        setContentView(R.layout.activity_exchange);
        
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(R.string.exchange_activity_title);
        
        // Initialize views
        cardBuyDoge = findViewById(R.id.card_buy_doge);
        cardSellDoge = findViewById(R.id.card_sell_doge);
        webViewExchange = findViewById(R.id.webview_exchange);
        progressBar = findViewById(R.id.progress_bar);
        
        // Setup WebView
        setupWebView();
        
        // Setup click listeners
        cardBuyDoge.setOnClickListener(v -> startExchange("buy"));
        cardSellDoge.setOnClickListener(v -> startExchange("sell"));
    }
    
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.exchange_options, menu);
        return true;
    }
    
    private void showExchangeSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.exchange_settings_title);
        
        // Create layout with API host and environment selection
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // API Host input
        android.widget.TextView apiHostLabel = new android.widget.TextView(this);
        apiHostLabel.setText(R.string.preferences_exchange_api_host_title);
        apiHostLabel.setPadding(0, 0, 0, 8);
        layout.addView(apiHostLabel);
        
        final EditText apiHostInput = new EditText(this);
        apiHostInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        apiHostInput.setText(config.getExchangeApiHost());
        apiHostInput.setHint(R.string.preferences_exchange_api_host_summary);
        apiHostInput.setPadding(16, 16, 16, 16);
        layout.addView(apiHostInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String apiHost = apiHostInput.getText().toString().trim();
            if (!apiHost.isEmpty()) {
                config.setExchangeApiHost(apiHost);
            }
            
            // Always set environment to production
            config.setExchangeEnvironment("prod");
            
            Toast.makeText(this, getString(R.string.exchange_settings_updated), Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        
        builder.show();
    }
    
    private void showErrorDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.exchange_error_title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
    
    private void setupWebView() {
        WebSettings webSettings = webViewExchange.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // Enable DOM storage for SDK
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setBlockNetworkImage(false);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webViewExchange.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webViewExchange.setVerticalScrollBarEnabled(true);
        webViewExchange.setHorizontalScrollBarEnabled(false);
        
        webViewExchange.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                log.info("Exchange WebView page finished loading: {}", url);
                progressBar.setVisibility(View.GONE);
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                log.error("WebView error: {} - {} - {}", errorCode, description, failingUrl);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ExchangeActivity.this, 
                        getString(R.string.exchange_error_loading) + ": " + description, 
                        Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Allow all URLs to load in WebView
                return false;
            }
        });
        
        // Set WebChromeClient to capture console logs for debugging
        webViewExchange.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                log.info("WebView Console [{}]: {} - {}", 
                    consoleMessage.messageLevel(), 
                    consoleMessage.message(), 
                    consoleMessage.sourceId());
                return true;
            }
        });
    }
    
    private void startExchange(String type) {
        exchangeType = type;
        
        // Show modal dialog with WebView
        showMetalPayModal(type);
    }
    
    private void showMetalPayModal(String type) {
        // Create custom dialog
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(R.layout.dialog_metal_pay_exchange);
        
        // Get views from dialog
        WebView modalWebView = dialog.findViewById(R.id.modal_webview_exchange);
        ProgressBar modalProgressBar = dialog.findViewById(R.id.modal_progress_bar);
        ImageButton btnClose = dialog.findViewById(R.id.btn_close_modal);
        
        // Setup WebView
        setupModalWebView(modalWebView);
        
        // Close button
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        // Dismiss on back press
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        
        // Show dialog
        dialog.show();
        
        // Make dialog full screen with rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                                         android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Store dialog reference for later use
        android.app.Dialog finalDialog = dialog;
        
        // Get wallet information for Metal Pay
        application.getWalletAsync(wallet -> {
            if (wallet == null) {
                runOnUiThread(() -> {
                    modalProgressBar.setVisibility(View.GONE);
                    Toast.makeText(ExchangeActivity.this, 
                        "Wallet not available. Please wait for wallet to sync.", 
                        Toast.LENGTH_LONG).show();
                    finalDialog.dismiss();
                });
                return;
            }
            
            // Get wallet address for buy or balance for sell
            String walletAddress = null;
            String walletBalance = null;
            
            // Execute wallet operations on background thread
            final String[] finalWalletAddress = {null};
            final String[] finalWalletBalance = {null};
            
            new android.os.AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    org.bitcoinj.core.Context.propagate(de.schildbach.wallet.Constants.CONTEXT);
                    
                    if ("buy".equals(type)) {
                        // Get current receive address for buying
                        org.bitcoinj.core.Address address = wallet.currentReceiveAddress();
                        if (address != null) {
                            finalWalletAddress[0] = address.toString();
                        }
                    } else if ("sell".equals(type)) {
                        // Get available balance for selling
                        org.bitcoinj.core.Coin balance = de.schildbach.wallet.util.ExcludedAddressHelper.getAvailableBalanceExcludingReserved(wallet);
                        if (balance != null && balance.isPositive()) {
                            // Convert to DOGE (divide by 100000000)
                            double dogeAmount = balance.getValue() / 100000000.0;
                            finalWalletBalance[0] = String.format("%.8f", dogeAmount);
                        }
                    }
                    return null;
                }
                
                @Override
                protected void onPostExecute(Void aVoid) {
                    // Get API host from configuration
                    String apiHost = config.getExchangeApiHost();
                    if (apiHost == null || apiHost.isEmpty()) {
                        apiHost = "https://api.dogecoinwallet.org";
                    }
                    
                    // Ensure API host ends with /
                    if (!apiHost.endsWith("/")) {
                        apiHost += "/";
                    }
                    
                    // Get selected fiat currency from wallet configuration
                    String fiatCurrency = config.getExchangeCurrencyCode();
                    log.info("Selected fiat currency from config: {}", fiatCurrency);
                    if (fiatCurrency == null || fiatCurrency.isEmpty()) {
                        // Fallback to USD if no currency is set
                        fiatCurrency = "USD";
                        log.warn("No currency set, using fallback: USD");
                    }
                    
                    // Fetch credentials from backend with wallet info
                    fetchExchangeCredentials(apiHost, type, finalWalletAddress[0], finalWalletBalance[0], fiatCurrency, modalWebView, modalProgressBar, finalDialog);
                }
            }.execute();
        });
    }
    
    private void setupModalWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setBlockNetworkImage(false);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(false);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                log.info("Modal WebView page finished loading: {}", url);
                runOnUiThread(() -> {
                    ProgressBar progressBar = ((android.app.Dialog) view.getTag()).findViewById(R.id.modal_progress_bar);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    view.setVisibility(View.VISIBLE);
                });
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                log.error("Modal WebView error: {} - {} - {}", errorCode, description, failingUrl);
                runOnUiThread(() -> {
                    ProgressBar progressBar = ((android.app.Dialog) view.getTag()).findViewById(R.id.modal_progress_bar);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(ExchangeActivity.this, 
                        getString(R.string.exchange_error_loading) + ": " + description, 
                        Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                log.info("Modal WebView Console [{}]: {} - {}", 
                    consoleMessage.messageLevel(), 
                    consoleMessage.message(), 
                    consoleMessage.sourceId());
                return true;
            }
        });
    }
    
    private void fetchExchangeCredentials(String apiHost, String type, String walletAddress, String walletBalance, String fiatCurrency, WebView webView, ProgressBar progressBar, android.app.Dialog dialog) {
        String url = apiHost + "v1/signature";
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();
        
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Error fetching exchange credentials", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ExchangeActivity.this, 
                        getString(R.string.exchange_error_connection), 
                        Toast.LENGTH_LONG).show();
                    // Show cards again
                    cardBuyDoge.setVisibility(View.VISIBLE);
                    cardSellDoge.setVisibility(View.VISIBLE);
                    webViewExchange.setVisibility(View.GONE);
                });
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    log.error("Error response from exchange API: {}", response.code());
                    String errorMessage = getString(R.string.exchange_error_api) + ": " + response.code();
                    
                    // Try to parse error message from response body
                    try {
                        String responseBody = response.body().string();
                        log.error("Error response body: {}", responseBody);
                        
                        // Try to extract error message from JSON
                        String errorDetail = extractJsonValue(responseBody, "error");
                        if (errorDetail != null && !errorDetail.isEmpty()) {
                            errorMessage = getString(R.string.exchange_error_api) + " (" + response.code() + "):\n" + errorDetail;
                            
                            // Provide helpful suggestions based on error
                            if (errorDetail.contains("SECRET_KEY") || errorDetail.contains("API_KEY")) {
                                errorMessage += "\n\n" + getString(R.string.exchange_error_missing_keys);
                            } else if (response.code() == 500) {
                                errorMessage += "\n\n" + getString(R.string.exchange_error_500_help);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error parsing error response", e);
                    }
                    
                    final String finalErrorMessage = errorMessage;
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        showErrorDialog(finalErrorMessage);
                        // Show cards again
                        cardBuyDoge.setVisibility(View.VISIBLE);
                        cardSellDoge.setVisibility(View.VISIBLE);
                        webViewExchange.setVisibility(View.GONE);
                    });
                    return;
                }
                
                try {
                    String responseBody = response.body().string();
                    log.info("Exchange API response: {}", responseBody);
                    
                    // Parse JSON response (simple parsing, could use Gson if available)
                    // Expected format: {"apiKey":"...","signature":"...","nonce":"..."}
                    String apiKey = extractJsonValue(responseBody, "apiKey");
                    String signature = extractJsonValue(responseBody, "signature");
                    String nonce = extractJsonValue(responseBody, "nonce");
                    
                    if (apiKey == null || signature == null || nonce == null) {
                        throw new IOException("Invalid response format");
                    }
                    
                    // Load Metal Pay Connect in WebView
                    final String finalApiKey = apiKey;
                    final String finalSignature = signature;
                    final String finalNonce = nonce;
                    final String finalWalletAddress = walletAddress;
                    final String finalWalletBalance = walletBalance;
                    final String finalFiatCurrency = fiatCurrency;
                    
                    log.info("Loading Metal Pay with currency: {}", finalFiatCurrency);
                    
                    runOnUiThread(() -> {
                        // Store dialog reference in WebView tag for access in callbacks
                        webView.setTag(dialog);
                        loadMetalPayConnect(finalApiKey, finalSignature, finalNonce, type, finalWalletAddress, finalWalletBalance, finalFiatCurrency, webView, progressBar);
                    });
                    
                } catch (Exception e) {
                    log.error("Error parsing exchange credentials", e);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ExchangeActivity.this, 
                            getString(R.string.exchange_error_parsing), 
                            Toast.LENGTH_LONG).show();
                        // Show cards again
                        cardBuyDoge.setVisibility(View.VISIBLE);
                        cardSellDoge.setVisibility(View.VISIBLE);
                        webViewExchange.setVisibility(View.GONE);
                    });
                }
            }
        });
    }
    
    private String extractJsonValue(String json, String key) {
        try {
            int keyIndex = json.indexOf("\"" + key + "\"");
            if (keyIndex == -1) return null;
            
            int valueStart = json.indexOf(":", keyIndex) + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            
            if (valueStart >= json.length()) return null;
            
            char quoteChar = json.charAt(valueStart);
            if (quoteChar != '"' && quoteChar != '\'') return null;
            
            int valueEnd = json.indexOf(quoteChar, valueStart + 1);
            if (valueEnd == -1) return null;
            
            return json.substring(valueStart + 1, valueEnd);
        } catch (Exception e) {
            log.error("Error extracting JSON value for key: " + key, e);
            return null;
        }
    }
    
    private void loadMetalPayConnect(String apiKey, String signature, String nonce, String type, String walletAddress, String walletBalance, String fiatCurrency, WebView webView, ProgressBar progressBar) {
        // Fetch the SDK script first to bypass MIME type issues
        fetchAndLoadMetalPayConnect(apiKey, signature, nonce, type, walletAddress, walletBalance, fiatCurrency, webView, progressBar);
    }
    
    private void fetchAndLoadMetalPayConnect(String apiKey, String signature, String nonce, String type, String walletAddress, String walletBalance, String fiatCurrency, WebView webView, ProgressBar progressBar) {
        progressBar.setVisibility(View.VISIBLE);
        
        // Try loading from local assets first, then fallback to CDN
        // The correct package name is "metal-pay-connect-js" (not @metalpay/metal-pay-connect)
        String[] sdkUrls = {
            // Try local assets first
            "file:///android_asset/metal-pay-connect.js",
            // Then try CDN with correct package name
            "https://unpkg.com/metal-pay-connect-js@latest/dist/index.cjs",
            "https://cdn.jsdelivr.net/npm/metal-pay-connect-js@latest/dist/index.cjs",
            "https://unpkg.com/metal-pay-connect-js@latest/dist/index.js",
            "https://cdn.jsdelivr.net/npm/metal-pay-connect-js@latest/dist/index.js",
            // Legacy URLs (may not work)
            "https://unpkg.com/@metalpay/metal-pay-connect@latest/dist/metal-pay-connect.js",
            "https://cdn.jsdelivr.net/npm/@metalpay/metal-pay-connect@latest/dist/metal-pay-connect.js"
        };
        
        fetchSdkWithFallback(sdkUrls, 0, apiKey, signature, nonce, type, walletAddress, walletBalance, fiatCurrency, webView, progressBar);
    }
    
    private void fetchSdkWithFallback(String[] urls, int index, String apiKey, String signature, String nonce, String type, String walletAddress, String walletBalance, String fiatCurrency, WebView webView, ProgressBar progressBar) {
        if (index >= urls.length) {
            log.error("All SDK URLs failed");
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                showErrorDialog(getString(R.string.exchange_error_sdk_load) + 
                    "\n\n" + getString(R.string.exchange_error_sdk_help));
            });
            return;
        }
        
        String url = urls[index];
        log.info("Trying to fetch SDK from: {}", url);
        
        // Check if it's a local asset file
        if (url.startsWith("file:///android_asset/")) {
            try {
                // Load from assets
                String assetPath = url.replace("file:///android_asset/", "");
                java.io.InputStream inputStream = getAssets().open(assetPath);
                java.util.Scanner scanner = new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A");
                String sdkScript = scanner.hasNext() ? scanner.next() : "";
                scanner.close();
                inputStream.close();
                
                log.info("Successfully loaded SDK from assets: {}", assetPath);
                
                // Generate HTML with the SDK script embedded
                String html = generateMetalPayConnectHtml(apiKey, signature, nonce, type, sdkScript, true, walletAddress, walletBalance, fiatCurrency);
                
                runOnUiThread(() -> {
                    // Use connect.metalpay.com as base URL for proper origin
                    webView.loadDataWithBaseURL("https://connect.metalpay.com/", html, "text/html", "UTF-8", null);
                });
                return;
            } catch (Exception e) {
                log.warn("Failed to load SDK from assets: {}", e.getMessage());
                // Try next URL
                fetchSdkWithFallback(urls, index + 1, apiKey, signature, nonce, type, walletAddress, walletBalance, fiatCurrency, webView, progressBar);
                return;
            }
        }
        
        // Fetch from URL
        Request request = new Request.Builder()
                .url(url)
                .build();
        
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch SDK from {}: {}", url, e.getMessage());
                // Try next URL
                fetchSdkWithFallback(urls, index + 1, apiKey, signature, nonce, type, walletAddress, walletBalance, fiatCurrency, webView, progressBar);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    log.warn("Failed to fetch SDK from {}: HTTP {}", url, response.code());
                    response.close();
                    // Try next URL
                    fetchSdkWithFallback(urls, index + 1, apiKey, signature, nonce, type, walletAddress, walletBalance, fiatCurrency, webView, progressBar);
                    return;
                }
                
                String sdkScript = response.body().string();
                response.close();
                
                log.info("Successfully fetched SDK from: {}", url);
                
                // Check if it's CommonJS format (index.cjs) - needs special handling
                boolean isCommonJS = url.contains("index.cjs") || sdkScript.contains("module.exports");
                
                // Generate HTML with the SDK script embedded
                String html = generateMetalPayConnectHtml(apiKey, signature, nonce, type, sdkScript, isCommonJS, walletAddress, walletBalance, fiatCurrency);
                
                runOnUiThread(() -> {
                    // Use connect.metalpay.com as base URL for proper origin
                    webView.loadDataWithBaseURL("https://connect.metalpay.com/", html, "text/html", "UTF-8", null);
                });
            }
        });
    }
    
    private String escapeForJavaScript(String str) {
        // Escape string for use in JavaScript eval() or as a string literal
        return "\"" + str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
    
    private String generateMetalPayConnectHtml(String apiKey, String signature, String nonce, String type, String sdkScript, boolean isCommonJS, String walletAddress, String walletBalance, String fiatCurrency) {
        // Always use production environment
        String environment = "prod";
        
        // Detect dark mode
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        // Escape parameters for JavaScript
        String escapedApiKey = apiKey.replace("'", "\\'").replace("\\", "\\\\");
        String escapedSignature = signature.replace("'", "\\'").replace("\\", "\\\\");
        String escapedNonce = nonce.replace("'", "\\'").replace("\\", "\\\\");
        String escapedWalletAddress = walletAddress != null ? walletAddress.replace("'", "\\'").replace("\\", "\\\\") : "";
        String escapedWalletBalance = walletBalance != null ? walletBalance.replace("'", "\\'").replace("\\", "\\\\") : "";
        
        // Load dependencies first if using CommonJS
        String dependenciesScript = "";
        String sdkLoadingScript = "";
        
        if (isCommonJS) {
            // Load lodash (UMD version) and qs from CDN before the SDK
            dependenciesScript = 
                "<script src='https://cdn.jsdelivr.net/npm/lodash@latest/lodash.min.js'></script>\n" +
                "<script src='https://cdn.jsdelivr.net/npm/qs@latest/dist/qs.js'></script>\n" +
                "<script>\n" +
                "  // Setup module system for CommonJS\n" +
                "  (function() {\n" +
                "    var moduleCache = {};\n" +
                "    window.require = function(moduleName) {\n" +
                "      if (moduleCache[moduleName]) return moduleCache[moduleName];\n" +
                "      var module = { exports: {} };\n" +
                "      if (moduleName === 'lodash-es' || moduleName === 'lodash') {\n" +
                "        // lodash from CDN is available as window._\n" +
                "        module.exports = window._ || {};\n" +
                "      } else if (moduleName === 'qs') {\n" +
                "        // qs from CDN might be available as window.qs or Qs\n" +
                "        var qsModule = window.qs || window.Qs || {};\n" +
                "        module.exports = qsModule;\n" +
                "      } else {\n" +
                "        throw new Error('Module not found: ' + moduleName);\n" +
                "      }\n" +
                "      moduleCache[moduleName] = module.exports;\n" +
                "      return module.exports;\n" +
                "    };\n" +
                "    // Initialize module.exports before SDK loads\n" +
                "    window.module = { exports: {} };\n" +
                "    console.log('CommonJS polyfill initialized');\n" +
                "  })();\n" +
                "</script>\n";
            
            // Escape the CommonJS script properly for eval()
            String escapedCjsScript = escapeForJavaScript(sdkScript);
            
            sdkLoadingScript = 
                "<script>\n" +
                "  try {\n" +
                "    eval(" + escapedCjsScript + ");\n" +
                "    if (window.module && window.module.exports) {\n" +
                "      if (window.module.exports.MetalPayConnect) {\n" +
                "        window.MetalPayConnect = window.module.exports.MetalPayConnect;\n" +
                "      } else if (window.module.exports.default && window.module.exports.default.MetalPayConnect) {\n" +
                "        window.MetalPayConnect = window.module.exports.default.MetalPayConnect;\n" +
                "      } else if (window.module.exports.default) {\n" +
                "        window.MetalPayConnect = window.module.exports.default;\n" +
                "      }\n" +
                "    }\n" +
                "  } catch(e) {\n" +
                "    console.error('Error loading SDK:', e);\n" +
                "    console.error('SDK Error details:', e.message, e.stack);\n" +
                "  }\n" +
                "</script>\n";
        } else {
            // ES module or already bundled
            String escapedScript = escapeForJavaScript(sdkScript);
            sdkLoadingScript = 
                "<script>\n" +
                "  try {\n" +
                "    eval(" + escapedScript + ");\n" +
                "  } catch(e) {\n" +
                "    console.error('Error loading SDK:', e);\n" +
                "  }\n" +
                "</script>\n";
        }
        
        // Escape fiat currency code
        String escapedFiatCurrency = fiatCurrency != null ? fiatCurrency.replace("'", "\\'").replace("\\", "\\\\") : "USD";
        
        // Log currency for debugging
        log.info("Building Metal Pay params with currency: {} (type: {})", fiatCurrency, type);
        
        // Build params object for Metal Pay
        StringBuilder paramsBuilder = new StringBuilder();
        paramsBuilder.append("apiKey: '").append(escapedApiKey).append("', ");
        paramsBuilder.append("signature: '").append(escapedSignature).append("', ");
        paramsBuilder.append("nonce: '").append(escapedNonce).append("', ");
        paramsBuilder.append("networks: ['dogecoin'], ");
        paramsBuilder.append("type: '").append(type).append("'");
        
        // Add fiat currency for both buy and sell operations
        // Metal Pay may expect 'fiatCurrency' or 'currency' - try both formats
        if (fiatCurrency != null && !fiatCurrency.isEmpty()) {
            paramsBuilder.append(", currency: '").append(escapedFiatCurrency).append("'");
            paramsBuilder.append(", fiatCurrency: '").append(escapedFiatCurrency).append("'");
        }
        
        // Add wallet address for buy
        if ("buy".equals(type) && walletAddress != null && !walletAddress.isEmpty()) {
            paramsBuilder.append(", address: { 'dogecoin': '").append(escapedWalletAddress).append("' }");
        }
        
        // Add balance for sell (if available)
        if ("sell".equals(type) && walletBalance != null && !walletBalance.isEmpty()) {
            paramsBuilder.append(", balance: '").append(escapedWalletBalance).append("'");
        }
        
        String paramsString = paramsBuilder.toString();
        String backgroundColor = isDarkMode ? "#1a1a1a" : "#fff";
        String textColor = isDarkMode ? "#ffffff" : "#000000";
        
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset='utf-8' />\n" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=yes' />\n" +
                "<style>\n" +
                "html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: auto; background: " + backgroundColor + "; color: " + textColor + "; }\n" +
                "#metal-pay-connect { width: 100%; min-height: 100%; position: relative; }\n" +
                "body { -webkit-overflow-scrolling: touch; }\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div id='metal-pay-connect'></div>\n" +
                dependenciesScript +
                sdkLoadingScript +
                "<script>\n" +
                "(function() {\n" +
                "  function initMetalPayConnect() {\n" +
                "    try {\n" +
                "      if (typeof MetalPayConnect === 'undefined') {\n" +
                "        console.error('MetalPayConnect is not defined');\n" +
                "        document.body.innerHTML = '<div style=\"padding: 20px; text-align: center; color: " + textColor + ";\">Error: Metal Pay Connect SDK failed to load. Please check your internet connection.</div>';\n" +
                "        return;\n" +
                "      }\n" +
                "      console.log('Initializing Metal Pay Connect...');\n" +
                "      var metalPayConnect = new MetalPayConnect({\n" +
                "        el: document.getElementById('metal-pay-connect'),\n" +
                "        environment: '" + environment + "',\n" +
                "        params: {\n" +
                "          " + paramsString + "\n" +
                "        }\n" +
                "      });\n" +
                "      console.log('Metal Pay Connect initialized successfully');\n" +
                "    } catch(e) {\n" +
                "      console.error('Metal Pay Connect error:', e);\n" +
                "      document.body.innerHTML = '<div style=\"padding: 20px; text-align: center; color: " + textColor + ";\">Error loading exchange: ' + (e.message || e.toString()) + '</div>';\n" +
                "    }\n" +
                "  }\n" +
                "  \n" +
                "  // Wait for DOM and SDK to be ready\n" +
                "  function waitForSDK() {\n" +
                "    if (typeof MetalPayConnect !== 'undefined') {\n" +
                "      // SDK is loaded, initialize after a short delay to ensure it's fully ready\n" +
                "      setTimeout(function() {\n" +
                "        initMetalPayConnect();\n" +
                "      }, 200);\n" +
                "    } else {\n" +
                "      // Check again after a short delay\n" +
                "      setTimeout(waitForSDK, 100);\n" +
                "    }\n" +
                "  }\n" +
                "  \n" +
                "  // Start waiting for SDK\n" +
                "  if (document.readyState === 'loading') {\n" +
                "    document.addEventListener('DOMContentLoaded', function() {\n" +
                "      setTimeout(waitForSDK, 100);\n" +
                "    });\n" +
                "  } else {\n" +
                "    setTimeout(waitForSDK, 100);\n" +
                "  }\n" +
                "  \n" +
                "  // Timeout after 10 seconds\n" +
                "  setTimeout(function() {\n" +
                "    if (typeof MetalPayConnect === 'undefined') {\n" +
                "      document.body.innerHTML = '<div style=\"padding: 20px; text-align: center; color: #d32f2f;\"><h3>Connection Error</h3><p>Failed to initialize Metal Pay Connect SDK.</p><p>Please check your internet connection and try again.</p></div>';\n" +
                "    }\n" +
                "  }, 10000);\n" +
                "})();\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }
    
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.exchange_options_settings) {
            showExchangeSettingsDialog();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            if (webViewExchange.getVisibility() == View.VISIBLE) {
                // Go back to main screen
                cardBuyDoge.setVisibility(View.VISIBLE);
                cardSellDoge.setVisibility(View.VISIBLE);
                webViewExchange.setVisibility(View.GONE);
                exchangeType = null;
                return true;
            } else {
                finish();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        if (webViewExchange.getVisibility() == View.VISIBLE) {
            // Go back to main screen
            cardBuyDoge.setVisibility(View.VISIBLE);
            cardSellDoge.setVisibility(View.VISIBLE);
            webViewExchange.setVisibility(View.GONE);
            exchangeType = null;
        } else {
            super.onBackPressed();
        }
    }
}

