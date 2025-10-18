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
import android.content.SharedPreferences;
import android.os.Build;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import de.schildbach.wallet.R;

/**
 * Helper class for biometric authentication
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class BiometricHelper {
    private static final String PREFS_NAME = "biometric_prefs";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_BIOMETRIC_SETUP = "biometric_setup";
    private static final String KEY_BIOMETRIC_AUTHENTICATED = "biometric_authenticated";
    private static final String KEY_BIOMETRIC_SESSION_TIME = "biometric_session_time";
    
    // Session timeout: 30 minutes (extended for better user experience)
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000;

    /**
     * Check if biometric authentication is available on the device
     */
    public static boolean isBiometricAvailable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        
        BiometricManager biometricManager = BiometricManager.from(context);
        int biometricStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        
        return biometricStatus == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Check if biometric authentication is enabled in app settings
     */
    public static boolean isBiometricEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    /**
     * Enable or disable biometric authentication
     */
    public static void setBiometricEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
    }

    /**
     * Check if biometric setup is completed
     */
    public static boolean isBiometricSetup(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_BIOMETRIC_SETUP, false);
    }

    /**
     * Mark biometric setup as completed
     */
    public static void setBiometricSetup(Context context, boolean setup) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_BIOMETRIC_SETUP, setup).apply();
    }

    /**
     * Check if user is currently authenticated (within session timeout)
     */
    public static boolean isAuthenticated(Context context) {
        if (!isBiometricEnabled(context)) {
            return true; // If biometric is disabled, consider user authenticated
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean authenticated = prefs.getBoolean(KEY_BIOMETRIC_AUTHENTICATED, false);
        long sessionTime = prefs.getLong(KEY_BIOMETRIC_SESSION_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        // Check if session has expired
        if (authenticated && (currentTime - sessionTime) > SESSION_TIMEOUT) {
            setAuthenticated(context, false);
            return false;
        }
        
        return authenticated;
    }

    /**
     * Set authentication status
     */
    public static void setAuthenticated(Context context, boolean authenticated) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_BIOMETRIC_AUTHENTICATED, authenticated);
        if (authenticated) {
            editor.putLong(KEY_BIOMETRIC_SESSION_TIME, System.currentTimeMillis());
        }
        editor.apply();
    }

    /**
     * Check if biometric authentication is required
     */
    public static boolean isBiometricRequired(Context context) {
        return isBiometricAvailable(context) && isBiometricEnabled(context) && !isAuthenticated(context);
    }

    /**
     * Show biometric authentication prompt
     */
    public static void showBiometricPrompt(FragmentActivity activity, BiometricPrompt.AuthenticationCallback callback) {
        if (!isBiometricAvailable(activity) || !isBiometricEnabled(activity)) {
            callback.onAuthenticationError(BiometricPrompt.ERROR_NO_BIOMETRICS, "Biometric authentication not available");
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_auth_title))
                .setSubtitle(activity.getString(R.string.biometric_auth_subtitle))
                .setNegativeButtonText(activity.getString(R.string.biometric_auth_cancel))
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, 
                ContextCompat.getMainExecutor(activity), callback);

        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * Show biometric setup prompt
     */
    public static void showBiometricSetupPrompt(FragmentActivity activity, BiometricPrompt.AuthenticationCallback callback) {
        if (!isBiometricAvailable(activity)) {
            callback.onAuthenticationError(BiometricPrompt.ERROR_NO_BIOMETRICS, "Biometric authentication not available");
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_setup_title))
                .setSubtitle(activity.getString(R.string.biometric_setup_subtitle))
                .setNegativeButtonText(activity.getString(R.string.biometric_auth_cancel))
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, 
                ContextCompat.getMainExecutor(activity), callback);

        biometricPrompt.authenticate(promptInfo);
    }
}
