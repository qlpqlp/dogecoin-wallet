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
        
        // Add minimal padding to account for action bar height
        // This prevents content from being hidden behind the action bar
        // Works for all devices by checking action bar height dynamically
        getWindow().getDecorView().post(new Runnable() {
            @Override
            public void run() {
                addMinimalActionBarPadding();
            }
        });
    }
    
    private void addMinimalActionBarPadding() {
        View contentView = findViewById(android.R.id.content);
        if (contentView == null) {
            return;
        }
        
        // Find the ListView inside PreferenceActivity (it contains the preference items)
        android.widget.ListView listView = null;
        if (contentView instanceof android.view.ViewGroup) {
            listView = findListView((android.view.ViewGroup) contentView);
        }
        
        // Get action bar height
        int actionBarHeight = 0;
        if (getActionBar() != null) {
            actionBarHeight = getActionBar().getHeight();
        }
        
        if (actionBarHeight == 0) {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = android.util.TypedValue.complexToDimensionPixelSize(
                    tv.data, getResources().getDisplayMetrics());
            }
        }
        
        if (actionBarHeight == 0) {
            actionBarHeight = (int) (getResources().getDisplayMetrics().density * 56);
        }
        
        // Check if ListView exists and check its actual position
        if (listView != null) {
            int[] location = new int[2];
            listView.getLocationOnScreen(location);
            int listViewTop = location[1];
            
            // Get action bar bottom position
            // Action bar is part of the window decor, calculate its position
            int actionBarBottom = 0;
            if (getActionBar() != null) {
                // Get action bar height
                int abHeight = getActionBar().getHeight();
                if (abHeight == 0) {
                    android.util.TypedValue tv = new android.util.TypedValue();
                    if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                        abHeight = android.util.TypedValue.complexToDimensionPixelSize(
                            tv.data, getResources().getDisplayMetrics());
                    }
                }
                
                // Get status bar height
                int statusBarHeight = 0;
                int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    statusBarHeight = getResources().getDimensionPixelSize(resourceId);
                }
                
                // Action bar bottom = status bar height + action bar height
                actionBarBottom = statusBarHeight + abHeight;
            }
            
            // If ListView top is less than action bar bottom, content is hidden
            if (listViewTop < actionBarBottom) {
                int neededPadding = actionBarBottom - listViewTop;
                int currentPadding = contentView.getPaddingTop();
                // Only add the difference needed, not the full action bar height
                if (neededPadding > currentPadding) {
                    contentView.setPadding(
                        contentView.getPaddingLeft(),
                        neededPadding,
                        contentView.getPaddingRight(),
                        contentView.getPaddingBottom()
                    );
                }
            }
        } else {
            // Fallback: check current padding and only add if minimal
            int currentTopPadding = contentView.getPaddingTop();
            // Only add padding if there's very little or no padding (less than 30% of action bar)
            // This prevents adding padding on devices that already have system padding
            if (currentTopPadding < (actionBarHeight * 0.3)) {
                // Add minimum needed, not full action bar height
                int minimalPadding = actionBarHeight;
                if (minimalPadding > currentTopPadding) {
                    contentView.setPadding(
                        contentView.getPaddingLeft(),
                        minimalPadding,
                        contentView.getPaddingRight(),
                        contentView.getPaddingBottom()
                    );
                }
            }
        }
    }
    
    private android.widget.ListView findListView(android.view.ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof android.widget.ListView) {
                return (android.widget.ListView) child;
            } else if (child instanceof android.view.ViewGroup) {
                android.widget.ListView found = findListView((android.view.ViewGroup) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
                || SafetyNotesPreferenceFragment.class.getName().equals(fragmentName)
                || TechnicalNotesPreferenceFragment.class.getName().equals(fragmentName)
                || BackupWalletPreferenceFragment.class.getName().equals(fragmentName)
                || RestoreWalletPreferenceFragment.class.getName().equals(fragmentName)
                || EncryptKeysPreferenceFragment.class.getName().equals(fragmentName)
                || ExchangeRatesPreferenceFragment.class.getName().equals(fragmentName)
                || NetworkMonitorPreferenceFragment.class.getName().equals(fragmentName)
                || ExtendedPublicKeyPreferenceFragment.class.getName().equals(fragmentName)
                || ResetBlockchainPreferenceFragment.class.getName().equals(fragmentName)
                || SweepWalletPreferenceFragment.class.getName().equals(fragmentName)
                || ReportIssuePreferenceFragment.class.getName().equals(fragmentName)
                || AboutFragment.class.getName().equals(fragmentName);
    }
}
