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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import de.schildbach.wallet.R;
import de.schildbach.wallet.util.BiometricHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity that handles biometric authentication for widget clicks and app shortcuts
 * 
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class BiometricAuthActivity extends FragmentActivity {
    private static final Logger log = LoggerFactory.getLogger(BiometricAuthActivity.class);
    
    public static final String EXTRA_TARGET_ACTIVITY = "target_activity";
    public static final String EXTRA_TARGET_INTENT = "target_intent";
    
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Always clear authentication state when launched from widget
        // This ensures fresh authentication is required
        BiometricHelper.setAuthenticated(this, false);
        log.info("BiometricAuthActivity: Cleared authentication state for fresh auth");
        
        // Check if biometric is enabled and required
        if (!BiometricHelper.isBiometricEnabled(this)) {
            log.info("BiometricAuthActivity: Biometric not enabled, proceeding directly");
            // Biometric not enabled, proceed directly to target
            proceedToTarget();
            return;
        }
        
        if (!BiometricHelper.isBiometricAvailable(this)) {
            log.info("BiometricAuthActivity: Biometric not available, proceeding directly");
            // Biometric not available, proceed directly to target
            proceedToTarget();
            return;
        }
        
        log.info("BiometricAuthActivity: Showing biometric authentication");
        // Add a small delay to ensure authentication state is properly cleared
        mainHandler.postDelayed(() -> {
            showBiometricAuthentication();
        }, 100);
    }
    
    private void showBiometricAuthentication() {
        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.biometric_auth_subtitle))
                .setConfirmationRequired(true) // Force confirmation
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        
        // When DEVICE_CREDENTIAL is included, don't set negative button text
        // The system will automatically show "Use PIN" or similar option
        BiometricPrompt.PromptInfo promptInfo = builder.build();
        
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, 
                ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                log.info("Biometric authentication successful for widget/shortcut");
                
                // Mark as authenticated
                BiometricHelper.setAuthenticated(BiometricAuthActivity.this, true);
                
                // Proceed to target activity
                proceedToTarget();
            }
            
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                log.warn("Biometric authentication error: {} - {}", errorCode, errString);
                
                // Authentication failed, finish this activity
                finish();
            }
            
            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                log.warn("Biometric authentication failed");
                // Don't finish, let user try again
            }
        });
        
        biometricPrompt.authenticate(promptInfo);
    }
    
    private void proceedToTarget() {
        Intent targetIntent = getIntent().getParcelableExtra(EXTRA_TARGET_INTENT);
        if (targetIntent != null) {
            // Use the provided intent
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(targetIntent);
        } else {
            // Use the target activity class name
            String targetActivity = getIntent().getStringExtra(EXTRA_TARGET_ACTIVITY);
            
            // Also check for shortcut intent extras
            if (targetActivity == null) {
                targetActivity = getIntent().getStringExtra("target_activity");
            }
            
            if (targetActivity != null) {
                try {
                    Class<?> targetClass = Class.forName(targetActivity);
                    Intent intent = new Intent(this, targetClass);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } catch (ClassNotFoundException e) {
                    log.error("Target activity class not found: {}", targetActivity, e);
                }
            } else {
                // No target specified, go to main wallet activity
                Intent intent = new Intent(this, WalletActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        }
        
        // Finish this activity
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}
