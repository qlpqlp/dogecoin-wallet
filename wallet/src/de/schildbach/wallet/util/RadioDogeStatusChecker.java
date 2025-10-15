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
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.WalletApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Helper class to check RadioDoge status and manage the banner display
 */
public class RadioDogeStatusChecker {
    private static final String RADIODOGE_STATUS_URL = "http://192.168.4.1/api/status";
    private static final int CONNECTION_TIMEOUT_MS = 1000; // Reduced from 3000ms
    private static final int READ_TIMEOUT_MS = 2000; // Reduced from 5000ms
    private static final int CHECK_INTERVAL_MS = 30000; // Check every 30 seconds (less frequent)
    
    private static final Logger log = LoggerFactory.getLogger(RadioDogeStatusChecker.class);
    
    private final Context context;
    private final Handler handler;
    private final Runnable statusCheckRunnable;
    private View radiodogeStatusBanner;
    private boolean isChecking = false;
    private boolean isApiCallInProgress = false;
    
    public RadioDogeStatusChecker(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.statusCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkRadioDogeStatus();
                if (isChecking) {
                    handler.postDelayed(this, CHECK_INTERVAL_MS);
                }
            }
        };
    }
    
    public void setBannerView(View bannerView) {
        this.radiodogeStatusBanner = bannerView;
    }
    
    public void startChecking() {
        if (!isChecking) {
            isChecking = true;
            // Add a small delay to avoid immediate checks when UI is loading
            handler.postDelayed(statusCheckRunnable, 200); // Reduced from 1000ms to 200ms
            log.info("Started RadioDoge status checking");
        }
    }
    
    public void stopChecking() {
        isChecking = false;
        handler.removeCallbacks(statusCheckRunnable);
        log.info("Stopped RadioDoge status checking");
    }
    
    private void checkRadioDogeStatus() {
        // Prevent multiple simultaneous API calls
        if (isApiCallInProgress) {
            return;
        }
        
        // Run the actual check in a background thread to avoid blocking UI
        new Thread(() -> {
            try {
                WalletApplication app = (WalletApplication) context.getApplicationContext();
                Configuration config = app.getConfiguration();
                
                // Only check if RadioDoge is enabled in settings
                if (!config.getRadioDogeEnabled()) {
                    hideBanner();
                    return;
                }
                
                // Check if we have internet connectivity
                boolean hasInternet = RadioDogeHelper.hasInternetConnectivity(context);
                if (hasInternet) {
                    hideBanner();
                    return;
                }
                
                // Check RadioDoge status via API (only if no internet)
                isApiCallInProgress = true;
                boolean isRadioDogeActive = checkRadioDogeStatusAPI();
                isApiCallInProgress = false;
                
                if (isRadioDogeActive) {
                    showBanner();
                } else {
                    hideBanner();
                }
                
            } catch (Exception e) {
                log.warn("Error checking RadioDoge status: {}", e.getMessage());
                hideBanner();
                isApiCallInProgress = false;
            }
        }).start();
    }
    
    private boolean checkRadioDogeStatusAPI() {
        try {
            URL url = new URL(RADIODOGE_STATUS_URL);
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
                
                String statusJson = response.toString();
                // Parse the JSON response to check if it's a valid RadioDoge device
                return parseRadioDogeStatus(statusJson);
            } else {
                return false;
            }
        } catch (Exception e) {
            // Silently fail to reduce log spam
            return false;
        }
    }
    
    private boolean parseRadioDogeStatus(String statusJson) {
        try {
            // Look for RadioDoge device indicators in the JSON response
            boolean hasSuccess = statusJson.contains("\"success\":true");
            boolean hasDevice = statusJson.contains("\"device\"");
            boolean hasRadioDogeName = statusJson.contains("\"name\":\"RadioDoge\"");
            boolean hasWifiRadioDoge = statusJson.contains("\"wifi\":\"RadioDoge\"");
            boolean hasReadyStatus = statusJson.contains("\"status\":\"Ready\"");
            
            return hasSuccess && hasDevice && (hasRadioDogeName || hasWifiRadioDoge) && hasReadyStatus;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void showBanner() {
        if (radiodogeStatusBanner != null) {
            handler.post(() -> {
                radiodogeStatusBanner.setVisibility(View.VISIBLE);
            });
        }
    }
    
    private void hideBanner() {
        if (radiodogeStatusBanner != null) {
            handler.post(() -> {
                radiodogeStatusBanner.setVisibility(View.GONE);
            });
        }
    }
}
