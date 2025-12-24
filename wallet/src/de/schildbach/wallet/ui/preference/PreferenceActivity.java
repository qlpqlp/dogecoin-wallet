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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;

import java.util.Iterator;
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
        
        // Android 16 (API 35) specific fix: Reset unwanted left and top margins
        // Android 16 applies additional window insets that create unwanted margins
        boolean isAndroid16 = Build.VERSION.SDK_INT >= 35; // Android 16 (API 35)
        
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
        
        // Get status bar height
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        
        // Calculate proper top padding
        int topPadding = 0;
        
        // Check if ListView exists and check its actual position
        if (listView != null) {
            int[] location = new int[2];
            listView.getLocationOnScreen(location);
            int listViewTop = location[1];
            
            // Get action bar bottom position
            // Action bar is part of the window decor, calculate its position
            int actionBarBottom = statusBarHeight + actionBarHeight;
            
            // If ListView top is less than action bar bottom, content is hidden
            if (listViewTop < actionBarBottom) {
                int neededPadding = actionBarBottom - listViewTop;
                int currentPadding = contentView.getPaddingTop();
                // Only add the difference needed, not the full action bar height
                if (neededPadding > currentPadding) {
                    topPadding = neededPadding;
                } else {
                    topPadding = currentPadding;
                }
            } else {
                topPadding = contentView.getPaddingTop();
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
                    topPadding = minimalPadding;
                } else {
                    topPadding = currentTopPadding;
                }
            } else {
                topPadding = currentTopPadding;
            }
        }
        
        // Android 16 (API 35) specific: Reset left padding to 0
        // We opt out of edge-to-edge in styles.xml, but still need to ensure left padding is 0
        if (isAndroid16) {
            // Reset left padding to 0 to remove unwanted left margin
            int leftPadding = 0;
            int rightPadding = contentView.getPaddingRight();
            int bottomPadding = contentView.getPaddingBottom();
            
            // Top padding should be handled by the ListView position calculation above
            // Since we opt out of edge-to-edge, we don't need special handling here
            contentView.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
        } else {
            // For Android 14 and 15, keep existing behavior but ensure left padding is correct
            int leftPadding = contentView.getPaddingLeft();
            // Only reset left padding if it seems incorrectly set (e.g., > 0 when it shouldn't be)
            // For Android 14/15, we typically don't want left padding on preference screens
            if (leftPadding > 0) {
                leftPadding = 0;
            }
            
            contentView.setPadding(leftPadding, topPadding, 
                contentView.getPaddingRight(), contentView.getPaddingBottom());
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
        
        // Filter headers based on Labs settings
        WalletApplication application = (WalletApplication) getApplication();
        if (application != null) {
            Configuration config = application.getConfiguration();
            
            // Remove Exchange header if Labs Exchange is disabled
            if (!config.getLabsExchangeEnabled()) {
                Iterator<Header> iterator = target.iterator();
                while (iterator.hasNext()) {
                    Header header = iterator.next();
                    // Check by intent component class name or by title
                    boolean isExchange = false;
                    if (header.intent != null) {
                        if (header.intent.getComponent() != null) {
                            String className = header.intent.getComponent().getClassName();
                            if (className != null && className.contains("ExchangeActivity")) {
                                isExchange = true;
                            }
                        } else {
                            // Check by title when component is not set
                            CharSequence title = header.title;
                            if (title != null && title.toString().equals(getString(R.string.exchange_menu_title))) {
                                isExchange = true;
                            }
                        }
                    }
                    if (isExchange) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }
        
        // Add section headers for better organization
        // We'll insert category headers at appropriate positions
        // Note: We need to insert them in reverse order to maintain correct indices
        
        // Support section header (before Safety Notes)
        int supportIndex = -1;
        for (int i = 0; i < target.size(); i++) {
            Header h = target.get(i);
            if (h.fragment != null && h.fragment.contains("SafetyNotesPreferenceFragment")) {
                supportIndex = i;
                break;
            }
        }
        if (supportIndex >= 0) {
            Header supportHeader = new Header();
            supportHeader.title = getString(R.string.preferences_category_support);
            supportHeader.fragment = null; // Empty header acts as section divider
            supportHeader.id = -1; // Mark as section header
            target.add(supportIndex, supportHeader);
        }
        
        // Advanced Tools section header (before Reset Block Chain)
        int advancedIndex = -1;
        for (int i = 0; i < target.size(); i++) {
            Header h = target.get(i);
            if (h.fragment != null && h.fragment.contains("ResetBlockchainPreferenceFragment")) {
                advancedIndex = i;
                break;
            }
        }
        if (advancedIndex >= 0) {
            Header advancedHeader = new Header();
            advancedHeader.title = getString(R.string.preferences_category_advanced_tools);
            advancedHeader.fragment = null;
            advancedHeader.id = -1; // Mark as section header
            target.add(advancedIndex, advancedHeader);
        }
        
        // Network & Information section header (before Exchange Rates)
        int networkIndex = -1;
        for (int i = 0; i < target.size(); i++) {
            Header h = target.get(i);
            if (h.fragment != null && h.fragment.contains("ExchangeRatesPreferenceFragment")) {
                networkIndex = i;
                break;
            }
        }
        if (networkIndex >= 0) {
            Header networkHeader = new Header();
            networkHeader.title = getString(R.string.preferences_category_network_information);
            networkHeader.fragment = null;
            networkHeader.id = -1; // Mark as section header
            target.add(networkIndex, networkHeader);
        }
        
        // Wallet Management section header (before Configuration)
        int walletIndex = -1;
        for (int i = 0; i < target.size(); i++) {
            Header h = target.get(i);
            if (h.fragment != null && h.fragment.contains("SettingsFragment")) {
                walletIndex = i;
                break;
            }
        }
        if (walletIndex >= 0) {
            Header walletHeader = new Header();
            walletHeader.title = getString(R.string.preferences_category_wallet_management);
            walletHeader.fragment = null;
            walletHeader.id = -1; // Mark as section header
            target.add(walletIndex, walletHeader);
        }
    }
    
    @Override
    public void onHeaderClick(Header header, int position) {
        // Handle headers with intents first (like Exchange)
        if (header.intent != null) {
            // Always create explicit intent for ExchangeActivity to ensure it works
            // Check if this is ExchangeActivity by component or title
            boolean isExchange = false;
            if (header.intent.getComponent() != null) {
                String className = header.intent.getComponent().getClassName();
                if (className != null && className.contains("ExchangeActivity")) {
                    isExchange = true;
                }
            } else {
                // Check by title when component is not set
                CharSequence title = header.title;
                if (title != null && title.toString().equals(getString(R.string.exchange_menu_title))) {
                    isExchange = true;
                }
            }
            
            if (isExchange) {
                // Always create explicit intent for ExchangeActivity
                Intent newIntent = new Intent(this, de.schildbach.wallet.ui.ExchangeActivity.class);
                startActivity(newIntent);
            } else {
                // For other intents, try to start normally
                try {
                    startActivity(header.intent);
                } catch (android.content.ActivityNotFoundException e) {
                    // If it fails, log and show error
                    android.util.Log.e("PreferenceActivity", "Failed to start activity from header", e);
                }
            }
            return;
        }
        // Prevent clicking on section headers (headers with fragment == null)
        if (header.fragment == null) {
            return; // Don't navigate, just return
        }
        super.onHeaderClick(header, position);
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        
        // Style section headers (headers with fragment == null) in yellow color
        // Use a post-delay to ensure the ListView is fully initialized
        getListView().post(new Runnable() {
            @Override
            public void run() {
                styleSectionHeaders();
            }
        });
    }
    
    private void styleSectionHeaders() {
        android.widget.ListView listView = getListView();
        if (listView == null) {
            return;
        }
        
        // Get the adapter
        android.widget.ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        
        // Style all visible section headers
        int firstVisible = listView.getFirstVisiblePosition();
        int lastVisible = firstVisible + listView.getChildCount();
        
        for (int i = firstVisible; i < lastVisible && i < adapter.getCount(); i++) {
            View view = listView.getChildAt(i - firstVisible);
            if (view != null) {
                Header header = (Header) adapter.getItem(i);
                if (header != null && header.fragment == null) {
                    // This is a section header - style it with appropriate color
                    TextView titleView = view.findViewById(android.R.id.title);
                    if (titleView != null) {
                        // Detect dark mode
                        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                        boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        // Use amber in dark mode, colorPrimary (golden) in light mode
                        int colorRes = isDarkMode ? R.color.amber : R.color.colorPrimary;
                        titleView.setTextColor(ContextCompat.getColor(PreferenceActivity.this, colorRes));
                    }
                }
            }
        }
        
        // Set up a listener to style headers when they become visible during scrolling
        listView.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
            }
            
            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem, 
                    int visibleItemCount, int totalItemCount) {
                // Style visible section headers
                android.widget.ListAdapter adapter = view.getAdapter();
                if (adapter != null) {
                    for (int i = firstVisibleItem; i < firstVisibleItem + visibleItemCount && i < totalItemCount; i++) {
                        View childView = view.getChildAt(i - firstVisibleItem);
                        if (childView != null) {
                            Header header = (Header) adapter.getItem(i);
                            if (header != null && header.fragment == null) {
                                // This is a section header - style it with appropriate color
                                TextView titleView = childView.findViewById(android.R.id.title);
                                if (titleView != null) {
                                    // Detect dark mode
                                    int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                                    boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                                    // Use amber in dark mode, colorPrimary (golden) in light mode
                                    int colorRes = isDarkMode ? R.color.amber : R.color.colorPrimary;
                                    titleView.setTextColor(ContextCompat.getColor(PreferenceActivity.this, colorRes));
                                }
                            }
                        }
                    }
                }
            }
        });
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
