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

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.Store;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Use Doge Activity
 * 
 * Shows a map of stores that accept Dogecoin using Leaflet.
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class UseDogeActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(UseDogeActivity.class);
    private static final String PREFS_JSON_SOURCES = "use_doge_json_sources";
    private static final String DEFAULT_JSON_SOURCE = "https://raw.githubusercontent.com/qlpqlp/dogecoin-stores/main/doge-stores.json";
    private static final int PERMISSION_REQUEST_LOCATION = 1001;
    
    private WalletApplication application;
    private Configuration config;
    private WebView webView;
    private RecyclerView recyclerStores;
    private EditText editFilter;
    private Button btnShowList;
    private ImageButton btnConfig;
    private LinearLayout layoutList;
    private ProgressBar progressBar;
    private TextView textEmpty;
    
    private LocationManager locationManager;
    private LocationListener locationListener;
    private double currentLat = 0;
    private double currentLon = 0;
    
    private List<Store> allStores = new ArrayList<>();
    private List<Store> filteredStores = new ArrayList<>();
    private StoresAdapter adapter;
    private Set<String> jsonSources = new HashSet<>();
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        application = getWalletApplication();
        config = application.getConfiguration();
        
        // Check if Use Doge is enabled
        if (!config.getUseDogeEnabled()) {
            Toast.makeText(this, "Use Doge is disabled in settings", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        setContentView(R.layout.activity_use_doge);
        
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(R.string.use_doge_activity_title);
        
        // Initialize views
        webView = findViewById(R.id.webview_map);
        recyclerStores = findViewById(R.id.recycler_stores);
        editFilter = findViewById(R.id.edit_filter);
        btnShowList = findViewById(R.id.btn_show_list);
        btnConfig = findViewById(R.id.btn_config);
        layoutList = findViewById(R.id.layout_list);
        progressBar = findViewById(R.id.progress_bar);
        textEmpty = findViewById(R.id.text_empty);
        
        // Setup WebView
        setupWebView();
        
        // Setup RecyclerView
        recyclerStores.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StoresAdapter();
        recyclerStores.setAdapter(adapter);
        
        // Load JSON sources from preferences
        loadJsonSources();
        
        // Setup filter
        editFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterStores(s.toString());
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Setup buttons with initial icon tinting
        updateShowListButtonIcon();
        
        btnShowList.setOnClickListener(v -> {
            if (layoutList.getVisibility() == View.VISIBLE) {
                layoutList.setVisibility(View.GONE);
                btnShowList.setText("Show List");
            } else {
                layoutList.setVisibility(View.VISIBLE);
                btnShowList.setText("Hide List");
            }
            updateShowListButtonIcon();
        });
        
        btnConfig.setOnClickListener(v -> showConfigDialog());
    }
    
    private void updateShowListButtonIcon() {
        // Detect dark mode for icon tinting
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int tintColor = isDarkMode ? 
            ContextCompat.getColor(this, android.R.color.white) : 
            ContextCompat.getColor(this, android.R.color.black);
        
        Drawable icon;
        if (layoutList.getVisibility() == View.VISIBLE) {
            icon = ContextCompat.getDrawable(this, R.drawable.ic_close_white_24dp);
        } else {
            icon = ContextCompat.getDrawable(this, R.drawable.ic_list_white_24dp);
        }
        
        if (icon != null) {
            icon.setTint(tintColor);
            btnShowList.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }
        
        // Request location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Check if we should show an explanation
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show explanation dialog
                new AlertDialog.Builder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("Use Doge needs location permission to show your current position on the map and help you find nearby stores that accept Dogecoin.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        ActivityCompat.requestPermissions(UseDogeActivity.this, 
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 
                            PERMISSION_REQUEST_LOCATION);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Toast.makeText(this, "Location permission is required to show your position on the map", Toast.LENGTH_LONG).show();
                    })
                    .show();
            } else {
                // Request permission directly
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_LOCATION);
            }
        } else {
            startLocationUpdates();
        }
        
        // Load stores
        loadStores();
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }
    
    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                currentLat = location.getLatitude();
                currentLon = location.getLongitude();
                updateMapCenter();
            }
            
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            
            @Override
            public void onProviderEnabled(String provider) {}
            
            @Override
            public void onProviderDisabled(String provider) {}
        };
        
        if (locationManager != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 100, locationListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 100, locationListener);
                
                // Get last known location
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation == null) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (lastLocation != null) {
                    currentLat = lastLocation.getLatitude();
                    currentLon = lastLocation.getLongitude();
                    // Zoom to user location immediately if map is ready
                    updateMapCenter();
                }
            } catch (Exception e) {
                log.error("Error starting location updates", e);
            }
        }
    }
    
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkLoads(false);
        webSettings.setBlockNetworkImage(false);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                log.info("WebView page finished loading");
                // Wait a bit for Leaflet to initialize
                view.postDelayed(() -> {
                    // Check if map is initialized
                    view.evaluateJavascript("typeof map !== 'undefined' && map !== null;", result -> {
                        if ("true".equals(result)) {
                            if (currentLat != 0 && currentLon != 0) {
                                updateMapCenter();
                            }
                        } else {
                            // Retry initialization
                            view.postDelayed(() -> {
                                view.evaluateJavascript("if (typeof initMap === 'function') initMap();", null);
                            }, 500);
                        }
                    });
                }, 1000);
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                log.error("WebView error: {} - {} - {}", errorCode, description, failingUrl);
                runOnUiThread(() -> {
                    Toast.makeText(UseDogeActivity.this, "Map loading error: " + description, Toast.LENGTH_LONG).show();
                });
            }
        });
        
        // Load HTML with Leaflet - use loadDataWithBaseURL for better compatibility
        String html = generateMapHtml();
        webView.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null);
    }
    
    private String generateMapHtml() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset='utf-8' />\n" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />\n" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css' />\n" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>\n" +
                "<style>\n" +
                "html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }\n" +
                "#map { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div id='map'></div>\n" +
                "<script>\n" +
                "var map;\n" +
                "var markers = [];\n" +
                "var stores = [];\n" +
                "function initMap() {\n" +
                "  try {\n" +
                "    map = L.map('map', { zoomControl: true }).setView([0, 0], 2);\n" +
                "    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {\n" +
                "      attribution: '© OpenStreetMap contributors',\n" +
                "      maxZoom: 19\n" +
                "    }).addTo(map);\n" +
                "    map.invalidateSize();\n" +
                "    if (typeof Android !== 'undefined' && Android.mapReady) {\n" +
                "      Android.mapReady();\n" +
                "    }\n" +
                "  } catch(e) {\n" +
                "    console.error('Map init error:', e);\n" +
                "  }\n" +
                "}\n" +
                "function addStore(store) {\n" +
                "  if (!map) return;\n" +
                "  try {\n" +
                "    stores.push(store);\n" +
                "    var markerSvg = '<svg width=\"24\" height=\"31\" viewBox=\"0 0 32 41\" xmlns=\"http://www.w3.org/2000/svg\">' +\n" +
                "      '<path d=\"M16 0C7.2 0 0 7.2 0 16c0 11.5 16 25 16 25s16-13.5 16-25C32 7.2 24.8 0 16 0z\" fill=\"#FFCC33\" stroke=\"#000000\" stroke-width=\"2\"/>' +\n" +
                "      '<text x=\"16\" y=\"22\" font-family=\"Arial, sans-serif\" font-size=\"18\" font-weight=\"bold\" fill=\"#000000\" text-anchor=\"middle\" dominant-baseline=\"middle\">Ð</text>' +\n" +
                "      '</svg>';\n" +
                "    var yellowIcon = L.icon({\n" +
                "      iconUrl: 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(markerSvg))),\n" +
                "      iconSize: [24, 31],\n" +
                "      iconAnchor: [12, 31],\n" +
                "      popupAnchor: [0, -31]\n" +
                "    });\n" +
                "    var marker = L.marker([store.lat, store.lon], {icon: yellowIcon}).addTo(map);\n" +
                "    var popupContent = '<div style=\"max-width: 250px;\"><b style=\"font-size: 16px;\">' + (store.name || 'Store') + '</b>';\n" +
                "    if (store.category) popupContent += '<br/><span style=\"color: #666; font-size: 12px;\">' + store.category + '</span>';\n" +
                "    if (store.description) popupContent += '<br/><p style=\"margin: 8px 0;\">' + store.description + '</p>';\n" +
                "    if (store.address) popupContent += '<br/><span style=\"color: #333;\"><span style=\"filter: brightness(0); display: inline-block;\">📍</span> ' + store.address + '</span>';\n" +
                "    if (store.location && store.country) popupContent += '<br/><span style=\"color: #666;\">' + store.location + ', ' + store.country + '</span>';\n" +
                "    if (store.phone) popupContent += '<br/><span style=\"color: #666;\"><span style=\"filter: brightness(0); display: inline-block;\">📞</span> ' + store.phone + '</span>';\n" +
                "    if (store.email) popupContent += '<br/><span style=\"color: #666;\"><span style=\"filter: brightness(0); display: inline-block;\">✉️</span> ' + store.email + '</span>';\n" +
                "    if (store.website) popupContent += '<br/><a href=\"' + store.website + '\" target=\"_blank\" style=\"color: #000000; text-decoration: none;\"><span style=\"filter: brightness(0); display: inline-block;\">🌐</span> Visit Website</a>';\n" +
                "    popupContent += '</div>';\n" +
                "    var popup = L.popup({maxWidth: 300}).setContent(popupContent);\n" +
                "    marker.bindPopup(popup);\n" +
                "    marker.on('click', function() { if (typeof Android !== 'undefined' && Android.onStoreClick) { Android.onStoreClick(JSON.stringify(store)); } });\n" +
                "    markers.push(marker);\n" +
                "  } catch(e) {\n" +
                "    console.error('Add store error:', e);\n" +
                "  }\n" +
                "}\n" +
                "function clearStores() {\n" +
                "  if (!map) return;\n" +
                "  markers.forEach(function(m) { map.removeLayer(m); });\n" +
                "  markers = [];\n" +
                "  stores = [];\n" +
                "}\n" +
                "function setCenter(lat, lon) {\n" +
                "  if (!map) return;\n" +
                "  map.setView([lat, lon], 15);\n" +
                "  map.invalidateSize();\n" +
                "}\n" +
                "function zoomToStore(lat, lon) {\n" +
                "  if (!map) return;\n" +
                "  map.setView([lat, lon], 15);\n" +
                "  map.invalidateSize();\n" +
                "}\n" +
                "window.addEventListener('load', function() {\n" +
                "  if (typeof L !== 'undefined') {\n" +
                "    initMap();\n" +
                "  } else {\n" +
                "    setTimeout(function() { if (typeof L !== 'undefined') initMap(); }, 1000);\n" +
                "  }\n" +
                "});\n" +
                "if (document.readyState === 'complete' || document.readyState === 'interactive') {\n" +
                "  setTimeout(initMap, 100);\n" +
                "}\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }
    
    private class WebAppInterface {
        @JavascriptInterface
        public void mapReady() {
            runOnUiThread(() -> {
                log.info("Map is ready");
                // Zoom to user location first if available
                if (currentLat != 0 && currentLon != 0) {
                    updateMapCenter();
                } else {
                    // If no location yet, try to get last known location
                    if (locationManager != null && ContextCompat.checkSelfPermission(UseDogeActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (lastLocation == null) {
                                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                            }
                            if (lastLocation != null) {
                                currentLat = lastLocation.getLatitude();
                                currentLon = lastLocation.getLongitude();
                                updateMapCenter();
                            }
                        } catch (Exception e) {
                            log.error("Error getting last known location", e);
                        }
                    }
                }
                // Reload stores when map is ready
                if (!allStores.isEmpty()) {
                    updateMap();
                }
            });
        }
        
        @JavascriptInterface
        public void onStoreClick(String storeJson) {
            runOnUiThread(() -> {
                try {
                    Store store = new Gson().fromJson(storeJson, Store.class);
                    if (store != null) {
                        showStoreDetailsDialog(store);
                    }
                } catch (Exception e) {
                    log.error("Error parsing store JSON", e);
                }
            });
        }
    }
    
    private void updateMapCenter() {
        if (currentLat != 0 && currentLon != 0) {
            // Use a higher zoom level (15) for better visibility of user location
            webView.evaluateJavascript(String.format(Locale.US, "setCenter(%f, %f);", currentLat, currentLon), null);
            // Also set zoom level to 15 for user location
            webView.evaluateJavascript(String.format(Locale.US, "if (typeof map !== 'undefined' && map !== null) { map.setView([%f, %f], 15); map.invalidateSize(); }", currentLat, currentLon), null);
        }
    }
    
    private void loadJsonSources() {
        SharedPreferences prefs = getSharedPreferences("use_doge", MODE_PRIVATE);
        String sourcesJson = prefs.getString(PREFS_JSON_SOURCES, "[]");
        Gson gson = new Gson();
        Type type = new TypeToken<Set<String>>(){}.getType();
        jsonSources = gson.fromJson(sourcesJson, type);
        if (jsonSources.isEmpty()) {
            jsonSources.add(DEFAULT_JSON_SOURCE);
        }
    }
    
    private void saveJsonSources() {
        SharedPreferences prefs = getSharedPreferences("use_doge", MODE_PRIVATE);
        Gson gson = new Gson();
        String sourcesJson = gson.toJson(jsonSources);
        prefs.edit().putString(PREFS_JSON_SOURCES, sourcesJson).apply();
    }
    
    private void loadStores() {
        progressBar.setVisibility(View.VISIBLE);
        textEmpty.setVisibility(View.GONE);
        
        new Thread(() -> {
            List<Store> loadedStores = new ArrayList<>();
            
            for (String source : jsonSources) {
                try {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder()
                            .url(source)
                            .build();
                    
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        Gson gson = new Gson();
                        Type type = new TypeToken<List<Store>>(){}.getType();
                        List<Store> stores = gson.fromJson(json, type);
                        if (stores != null) {
                            loadedStores.addAll(stores);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error loading stores from " + source, e);
                }
            }
            
            final List<Store> finalStores = loadedStores;
            runOnUiThread(() -> {
                allStores = finalStores;
                filteredStores = new ArrayList<>(allStores);
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
                if (allStores.isEmpty()) {
                    textEmpty.setVisibility(View.VISIBLE);
                } else {
                    textEmpty.setVisibility(View.GONE);
                }
                // Update map after stores are loaded
                updateMap();
            });
        }).start();
    }
    
    private void updateMap() {
        if (webView == null) return;
        webView.post(() -> {
            // Check if map is initialized before updating
            webView.evaluateJavascript("typeof map !== 'undefined' && map !== null;", result -> {
                if ("true".equals(result)) {
                    // Clear existing stores
                    webView.evaluateJavascript("if (typeof clearStores === 'function') clearStores();", null);
                    // Add stores with a small delay to ensure clearStores completes
                    webView.postDelayed(() -> {
                        for (Store store : filteredStores) {
                            String storeJson = new Gson().toJson(store);
                            webView.evaluateJavascript(String.format("if (typeof addStore === 'function') addStore(%s);", storeJson), null);
                        }
                    }, 100);
                } else {
                    // Map not ready yet, retry after a delay
                    webView.postDelayed(() -> updateMap(), 500);
                }
            });
        });
    }
    
    private void filterStores(String query) {
        filteredStores.clear();
        if (query.isEmpty()) {
            filteredStores.addAll(allStores);
        } else {
            String lowerQuery = query.toLowerCase();
            for (Store store : allStores) {
                // Check each field safely, handling null values
                boolean matches = false;
                if (store.name != null && store.name.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                } else if (store.description != null && store.description.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                } else if (store.category != null && store.category.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                } else if (store.address != null && store.address.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                } else if (store.location != null && store.location.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                } else if (store.country != null && store.country.toLowerCase().contains(lowerQuery)) {
                    matches = true;
                }
                
                if (matches) {
                    filteredStores.add(store);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateMap();
    }
    
    private void zoomToStore(Store store) {
        webView.evaluateJavascript(String.format(Locale.US, "zoomToStore(%f, %f);", store.lat, store.lon), null);
        layoutList.setVisibility(View.GONE);
        btnShowList.setText("Show List");
        btnShowList.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_list_white_24dp, 0, 0, 0);
    }
    
    private void showStoreDetailsDialog(Store store) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_store_details, null);
        builder.setView(dialogView);
        builder.setTitle(store.name);
        
        TextView textCategory = dialogView.findViewById(R.id.text_category);
        TextView textDescription = dialogView.findViewById(R.id.text_description);
        TextView textAddress = dialogView.findViewById(R.id.text_address);
        TextView textLocation = dialogView.findViewById(R.id.text_location);
        TextView textPhone = dialogView.findViewById(R.id.text_phone);
        TextView textEmail = dialogView.findViewById(R.id.text_email);
        TextView textWebsite = dialogView.findViewById(R.id.text_website);
        Button btnZoom = dialogView.findViewById(R.id.btn_zoom);
        Button btnShare = dialogView.findViewById(R.id.btn_share);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        if (store.category != null && !store.category.isEmpty()) {
            textCategory.setText(store.category);
            textCategory.setVisibility(View.VISIBLE);
        } else {
            textCategory.setVisibility(View.GONE);
        }
        
        if (store.description != null && !store.description.isEmpty()) {
            textDescription.setText(store.description);
            textDescription.setVisibility(View.VISIBLE);
        } else {
            textDescription.setVisibility(View.GONE);
        }
        
        if (store.address != null && !store.address.isEmpty()) {
            textAddress.setText(store.address);
            textAddress.setVisibility(View.VISIBLE);
        } else {
            textAddress.setVisibility(View.GONE);
        }
        
        String locationText = "";
        if (store.location != null && !store.location.isEmpty()) {
            locationText = store.location;
        }
        if (store.country != null && !store.country.isEmpty()) {
            if (!locationText.isEmpty()) locationText += ", ";
            locationText += store.country;
        }
        if (!locationText.isEmpty()) {
            textLocation.setText(locationText);
            textLocation.setVisibility(View.VISIBLE);
        } else {
            textLocation.setVisibility(View.GONE);
        }
        
        if (store.phone != null && !store.phone.isEmpty()) {
            textPhone.setText(store.phone);
            textPhone.setVisibility(View.VISIBLE);
        } else {
            textPhone.setVisibility(View.GONE);
        }
        
        if (store.email != null && !store.email.isEmpty()) {
            textEmail.setText(store.email);
            textEmail.setVisibility(View.VISIBLE);
        } else {
            textEmail.setVisibility(View.GONE);
        }
        
        if (store.website != null && !store.website.isEmpty()) {
            textWebsite.setText(store.website);
            textWebsite.setVisibility(View.VISIBLE);
            textWebsite.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(store.website));
                startActivity(intent);
            });
        } else {
            textWebsite.setVisibility(View.GONE);
        }
        
        AlertDialog dialog = builder.create();
        
        btnZoom.setOnClickListener(v -> {
            zoomToStore(store);
            dialog.dismiss();
        });
        
        btnShare.setOnClickListener(v -> {
            shareStore(store);
            dialog.dismiss();
        });
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void shareStore(Store store) {
        StringBuilder shareText = new StringBuilder();
        
        // Store name (always include if available)
        if (store.name != null && !store.name.trim().isEmpty()) {
            shareText.append(store.name).append("\n");
        }
        
        // Category
        if (store.category != null && !store.category.trim().isEmpty()) {
            shareText.append("Category: ").append(store.category).append("\n");
        }
        
        // Description
        if (store.description != null && !store.description.trim().isEmpty()) {
            shareText.append(store.description).append("\n");
        }
        
        // Address
        if (store.address != null && !store.address.trim().isEmpty()) {
            shareText.append("📍 ").append(store.address).append("\n");
        }
        
        // Location and Country
        String locationText = "";
        if (store.location != null && !store.location.trim().isEmpty()) {
            locationText = store.location;
        }
        if (store.country != null && !store.country.trim().isEmpty()) {
            if (!locationText.isEmpty()) {
                locationText += ", ";
            }
            locationText += store.country;
        }
        if (!locationText.isEmpty()) {
            shareText.append(locationText).append("\n");
        }
        
        // Phone
        if (store.phone != null && !store.phone.trim().isEmpty()) {
            shareText.append("📞 ").append(store.phone).append("\n");
        }
        
        // Email
        if (store.email != null && !store.email.trim().isEmpty()) {
            shareText.append("✉️ ").append(store.email).append("\n");
        }
        
        // Website
        if (store.website != null && !store.website.trim().isEmpty()) {
            shareText.append("🌐 ").append(store.website).append("\n");
        }
        
        // Google Maps link
        String googleMapsUrl = String.format(Locale.US, "https://www.google.com/maps?q=%f,%f", store.lat, store.lon);
        shareText.append("\n").append("📍 ").append(googleMapsUrl);
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        
        // Also create a Google Maps intent
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsUrl));
        
        Intent chooser = Intent.createChooser(shareIntent, "Share store location");
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{mapIntent});
        startActivity(chooser);
    }
    
    private void showConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_json_sources, null);
        builder.setView(dialogView);
        builder.setTitle("JSON Sources");
        
        RecyclerView recyclerSources = dialogView.findViewById(R.id.recycler_sources);
        Button btnAdd = dialogView.findViewById(R.id.btn_add);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        List<String> sourcesList = new ArrayList<>(jsonSources);
        JsonSourcesAdapter sourcesAdapter = new JsonSourcesAdapter(sourcesList);
        recyclerSources.setLayoutManager(new LinearLayoutManager(this));
        recyclerSources.setAdapter(sourcesAdapter);
        
        AlertDialog dialog = builder.create();
        
        btnAdd.setOnClickListener(v -> showAddSourceDialog(sourcesList, sourcesAdapter));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void showAddSourceDialog(List<String> sourcesList, JsonSourcesAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_json_source, null);
        builder.setView(dialogView);
        builder.setTitle("Add JSON Source");
        
        EditText editUrl = dialogView.findViewById(R.id.edit_url);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String url = editUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show();
                return;
            }
            
            jsonSources.add(url);
            sourcesList.add(url);
            adapter.notifyItemInserted(sourcesList.size() - 1);
            saveJsonSources();
            dialog.dismiss();
            Toast.makeText(this, "Source added", Toast.LENGTH_SHORT).show();
        });
        
        dialog.show();
    }
    
    private void showEditSourceDialog(String oldUrl, int position, JsonSourcesAdapter adapter, List<String> sourcesList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_json_source, null);
        builder.setView(dialogView);
        builder.setTitle("Edit JSON Source");
        
        EditText editUrl = dialogView.findViewById(R.id.edit_url);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        editUrl.setText(oldUrl);
        
        AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String url = editUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show();
                return;
            }
            
            jsonSources.remove(oldUrl);
            jsonSources.add(url);
            sourcesList.set(position, url);
            adapter.notifyItemChanged(position);
            saveJsonSources();
            dialog.dismiss();
            Toast.makeText(this, "Source updated", Toast.LENGTH_SHORT).show();
        });
        
        dialog.show();
    }
    
    private class JsonSourcesAdapter extends RecyclerView.Adapter<JsonSourcesAdapter.SourceViewHolder> {
        private List<String> sources;
        
        JsonSourcesAdapter(List<String> sources) {
            this.sources = sources;
        }
        
        @NonNull
        @Override
        public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_json_source, parent, false);
            return new SourceViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
            String source = sources.get(position);
            holder.textUrl.setText(source);
            holder.btnEdit.setOnClickListener(v -> {
                showEditSourceDialog(source, position, this, sources);
            });
            holder.btnDelete.setOnClickListener(v -> {
                if (sources.size() <= 1) {
                    Toast.makeText(UseDogeActivity.this, "Cannot delete the last source", Toast.LENGTH_SHORT).show();
                    return;
                }
                jsonSources.remove(source);
                sources.remove(position);
                notifyItemRemoved(position);
                saveJsonSources();
                Toast.makeText(UseDogeActivity.this, "Source removed", Toast.LENGTH_SHORT).show();
            });
        }
        
        @Override
        public int getItemCount() {
            return sources.size();
        }
        
        class SourceViewHolder extends RecyclerView.ViewHolder {
            TextView textUrl;
            ImageButton btnEdit;
            ImageButton btnDelete;
            
            SourceViewHolder(View itemView) {
                super(itemView);
                textUrl = itemView.findViewById(R.id.text_url);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }
    
    private class StoresAdapter extends RecyclerView.Adapter<StoresAdapter.StoreViewHolder> {
        @NonNull
        @Override
        public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_store, parent, false);
            return new StoreViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
            Store store = filteredStores.get(position);
            holder.bind(store);
        }
        
        @Override
        public int getItemCount() {
            return filteredStores.size();
        }
        
        class StoreViewHolder extends RecyclerView.ViewHolder {
            private TextView textName;
            private TextView textCategory;
            private TextView textAddress;
            private ImageButton btnZoom;
            private ImageButton btnShare;
            
            StoreViewHolder(View itemView) {
                super(itemView);
                textName = itemView.findViewById(R.id.text_name);
                textCategory = itemView.findViewById(R.id.text_category);
                textAddress = itemView.findViewById(R.id.text_address);
                btnZoom = itemView.findViewById(R.id.btn_zoom);
                btnShare = itemView.findViewById(R.id.btn_share);
            }
            
            void bind(Store store) {
                textName.setText(store.name);
                textCategory.setText(store.category);
                textAddress.setText(store.address);
                
                btnZoom.setOnClickListener(v -> zoomToStore(store));
                btnShare.setOnClickListener(v -> shareStore(store));
                
                itemView.setOnClickListener(v -> zoomToStore(store));
            }
        }
    }
}


