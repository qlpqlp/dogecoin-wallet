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

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.wallet.R;
import de.schildbach.wallet.util.BiometricHelper;

/**
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class BiometricAuthDialogFragment extends DialogFragment {

    private static final Logger log = LoggerFactory.getLogger(BiometricAuthDialogFragment.class);
    private static final String FRAGMENT_TAG = BiometricAuthDialogFragment.class.getName();

    public interface BiometricAuthCallback {
        void onBiometricAuthSuccess();
        void onBiometricAuthError(String error);
        void onBiometricAuthCancelled();
    }

    private BiometricAuthCallback callback;

    public static void show(final FragmentManager fm, final BiometricAuthCallback callback) {
        // Check if fragment already exists to prevent duplicates
        Fragment existingFragment = fm.findFragmentByTag(FRAGMENT_TAG);
        if (existingFragment != null) {
            // Fragment already exists, just update callback
            if (existingFragment instanceof BiometricAuthDialogFragment) {
                ((BiometricAuthDialogFragment) existingFragment).setCallback(callback);
            }
            return;
        }
        
        final BiometricAuthDialogFragment newFragment = new BiometricAuthDialogFragment();
        newFragment.setCallback(callback);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    public void setCallback(BiometricAuthCallback callback) {
        this.callback = callback;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final WalletActivity activity = (WalletActivity) getActivity();
        
        if (!BiometricHelper.isBiometricAvailable(activity)) {
            if (callback != null) {
                callback.onBiometricAuthError(getString(R.string.biometric_not_available));
            }
            dismiss();
            return super.onCreateDialog(savedInstanceState);
        }

        // Return a simple dialog that will show the biometric prompt after creation
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.biometric_auth_title)
                .setMessage(R.string.biometric_auth_subtitle)
                .setNegativeButton(R.string.biometric_auth_cancel, (dialog1, which) -> {
                    if (callback != null) {
                        callback.onBiometricAuthCancelled();
                    }
                })
                .create();
        
        // Make dialog non-dismissible by back button or outside touch
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        
        // Use a Handler to delay the biometric prompt until after FragmentManager transactions are complete
        final WalletActivity activity = (WalletActivity) getActivity();
        if (activity != null && BiometricHelper.isBiometricAvailable(activity)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() != null && !isDetached()) {
                        BiometricHelper.showBiometricPrompt(activity, new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                                log.info("Biometric authentication succeeded");
                                if (callback != null) {
                                    callback.onBiometricAuthSuccess();
                                }
                                dismiss();
                            }

                            @Override
                            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                                log.warn("Biometric authentication error: {} - {}", errorCode, errString);
                                if (callback != null) {
                                    String errorMessage = getErrorMessage(errorCode);
                                    callback.onBiometricAuthError(errorMessage);
                                }
                                dismiss();
                            }

                            @Override
                            public void onAuthenticationFailed() {
                                log.warn("Biometric authentication failed");
                                // Don't dismiss on failure, let user try again
                            }
                        });
                    }
                }
            });
        }
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case BiometricPrompt.ERROR_NO_BIOMETRICS:
                return getString(R.string.biometric_error_no_biometrics);
            case BiometricPrompt.ERROR_HW_UNAVAILABLE:
                return getString(R.string.biometric_error_hw_unavailable);
            case BiometricPrompt.ERROR_HW_NOT_PRESENT:
                return getString(R.string.biometric_error_no_hardware);
            case BiometricPrompt.ERROR_CANCELED:
                return getString(R.string.biometric_error_user_cancel);
            case BiometricPrompt.ERROR_LOCKOUT:
                return getString(R.string.biometric_error_lockout);
            case BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                return getString(R.string.biometric_error_permanent_lockout);
            default:
                return getString(R.string.biometric_not_available);
        }
    }
}
