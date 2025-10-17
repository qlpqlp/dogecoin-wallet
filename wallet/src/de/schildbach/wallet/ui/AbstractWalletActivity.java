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

import android.app.ActivityManager.TaskDescription;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import androidx.fragment.app.FragmentActivity;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Toast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletActivity extends FragmentActivity {
    private WalletApplication application;

    protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Add padding for status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    addStatusBarPadding();
                }
            });
        }
        
        application = (WalletApplication) getApplication();
        setTaskDescription(new TaskDescription(null, null, getColor(R.color.bg_action_bar)));
    }
    
    private void addStatusBarPadding() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int statusBarHeight = getStatusBarHeight();
            // Add moderate extra padding to ensure content is fully visible
            int extraPadding = (int) (getResources().getDisplayMetrics().density * 55); // 55dp extra
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

    public WalletApplication getWalletApplication() {
        return application;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setShowWhenLocked(final boolean showWhenLocked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
            super.setShowWhenLocked(showWhenLocked);
        else if (showWhenLocked)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    public void startExternalDocument(final Uri url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } catch (final ActivityNotFoundException x) {
            log.info("Cannot view {}", url);
            new Toast(this).longToast(R.string.toast_start_external_document_failed);
        }
    }
}
