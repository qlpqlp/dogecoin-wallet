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
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
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
            // On Android 15 (API 35) and above, we opt out of edge-to-edge
            // With opt-out, the system handles padding automatically - don't modify it
            boolean isAndroid15OrAbove = Build.VERSION.SDK_INT >= 35; // Android 15 (API 35)
            
            if (isAndroid15OrAbove) {
                // With edge-to-edge opt-out, don't modify padding - let the system handle it
                // Only reset left padding to 0 if needed
                View contentView = findViewById(android.R.id.content);
                if (contentView != null && contentView.getPaddingLeft() > 0) {
                    contentView.setPadding(0, contentView.getPaddingTop(), 
                        contentView.getPaddingRight(), contentView.getPaddingBottom());
                }
                return; // Don't add any padding on Android 15 and above
            }
            
            // For Android below 15 (API < 35), don't add any padding
            // The system should handle it naturally, and adding padding causes content
            // to have unwanted margins and prevents bottom bar from sticking to bottom
            View contentView = findViewById(android.R.id.content);
            if (contentView != null) {
                // Only reset padding to 0 to ensure no unwanted margins
                contentView.setPadding(0, 0, 0, 0);
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
