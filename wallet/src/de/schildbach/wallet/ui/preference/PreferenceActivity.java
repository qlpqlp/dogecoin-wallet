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

package de.schildbach.wallet.ui.preference;

import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import de.schildbach.wallet.R;

import java.util.List;

/**
 * @author Andreas Schildbach
 */
public final class PreferenceActivity extends android.preference.PreferenceActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Add extra padding for settings page
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    addSettingsPadding();
                }
            });
        }
    }
    
    private void addSettingsPadding() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int statusBarHeight = getStatusBarHeight();
            // Add extra padding for settings page
            int extraPadding = (int) (getResources().getDisplayMetrics().density * 60); // 60dp extra for settings
            int totalPadding = statusBarHeight + extraPadding;
            
            // Get navigation bar height and add it to bottom padding
            int navigationBarHeight = getNavigationBarHeight();
            
            // Dynamic bottom padding based on screen size + navigation bar
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            
            // Calculate dynamic bottom padding (smaller for larger screens)
            int baseBottomPadding = (int) (getResources().getDisplayMetrics().density * 8); // 8dp base
            int dynamicBottomPadding = Math.max(baseBottomPadding, screenHeight / 100); // Scale with screen height
            int maxBottomPadding = (int) (getResources().getDisplayMetrics().density * 20); // Max 20dp
            int calculatedBottomPadding = Math.min(dynamicBottomPadding, maxBottomPadding);
            
            // Add navigation bar height to ensure content is above it
            int finalBottomPadding = calculatedBottomPadding + navigationBarHeight;
            
            View contentView = findViewById(android.R.id.content);
            if (contentView != null) {
                contentView.setPadding(0, totalPadding, 0, finalBottomPadding);
            }
        }
    }
    
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
    
    private int getNavigationBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }
    
    @Override
    public void onBuildHeaders(final List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        return SettingsFragment.class.getName().equals(fragmentName)
                || ExchangeRatesPreferenceFragment.class.getName().equals(fragmentName)
                || NetworkMonitorPreferenceFragment.class.getName().equals(fragmentName)
                || ExtendedPublicKeyPreferenceFragment.class.getName().equals(fragmentName)
                || ResetBlockchainPreferenceFragment.class.getName().equals(fragmentName)
                || ReportIssuePreferenceFragment.class.getName().equals(fragmentName)
                || AboutFragment.class.getName().equals(fragmentName);
    }
}
