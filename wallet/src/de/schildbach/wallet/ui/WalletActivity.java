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
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.res.Resources;
import android.widget.EditText;
import android.widget.Toast;
import android.os.Build;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import com.google.common.primitives.Floats;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.RecurringPaymentsService;
import de.schildbach.wallet.ui.InputParser.BinaryInputParser;
import de.schildbach.wallet.ui.InputParser.StringInputParser;
import de.schildbach.wallet.ui.backup.BackupWalletActivity;
import de.schildbach.wallet.ui.backup.BackupWalletDialogFragment;
import de.schildbach.wallet.ui.backup.RestoreWalletDialogFragment;
import de.schildbach.wallet.ui.monitor.NetworkMonitorActivity;
import de.schildbach.wallet.ui.preference.PreferenceActivity;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.ui.send.SweepWalletActivity;
import de.schildbach.wallet.ui.DigitalSignatureActivity;
import de.schildbach.wallet.ui.AccountingReportsActivity;
import de.schildbach.wallet.ui.FamilyModeActivity;
import de.schildbach.wallet.ui.FirstTimeSetupDialogFragment;
import de.schildbach.wallet.data.FamilyMemberDatabase;
import de.schildbach.wallet.util.BiometricHelper;
import de.schildbach.wallet.util.CrashReporter;
import de.schildbach.wallet.util.PendingTransactionRetryService;
import de.schildbach.wallet.util.RadioDogeHelper;
import de.schildbach.wallet.util.RadioDogeLogChecker;
import de.schildbach.wallet.util.RadioDogeStatusChecker;
import de.schildbach.wallet.util.Nfc;
import de.schildbach.wallet.util.OnFirstPreDraw;
import org.bitcoinj.core.PrefixedChecksummedBytes;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.script.Script;

import java.io.File;

/**
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public final class WalletActivity extends AbstractWalletActivity {
    public static final String INTENT_EXTRA_SHOW_REPORT_ISSUE = "show_report_issue";
    
    private WalletApplication application;
    private Handler handler = new Handler();
    private PendingTransactionRetryService pendingTransactionRetryService;
    private RadioDogeStatusChecker radiodogeStatusChecker;
    private View radiodogeStatusBanner;

    private AnimatorSet enterAnimation;
    private View contentView;
    private View levitateView;

    private AbstractWalletActivityViewModel walletActivityViewModel;
    private WalletActivityViewModel viewModel;
    

    private static final int REQUEST_CODE_SCAN = 0;
    private static final int REQUEST_CODE_SCAN_CHILD_ACTIVATION = 1001;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        application = getWalletApplication();
        final Configuration config = application.getConfiguration();
        
        // Check if terminal mode is enabled and redirect
        if (config.getPaymentTerminalEnabled() && !getClass().equals(PaymentTerminalActivity.class)) {
            final Intent intent = new Intent(this, PaymentTerminalActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        // Make app icon launches behave exactly like widget launches
        // If biometric is enabled, route through BiometricAuthActivity first (no sensitive data shown)
        // Otherwise, restart with CLEAR_TASK to force fresh instance for animation
        final Intent intent = getIntent();
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction()) 
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            // This is an app icon launch - match widget behavior
            // Only do this if savedInstanceState is null (fresh creation, not activity recreation from config change)
            // Skip if this intent already has the restart flag to prevent infinite loop
            if (savedInstanceState == null && !intent.getBooleanExtra("already_restarted", false)) {
                // Check if biometric is enabled and available (like widget does)
                if (BiometricHelper.isBiometricEnabled(this) && BiometricHelper.isBiometricAvailable(this)) {
                    // Route through BiometricAuthActivity first - no sensitive data shown before auth
                    final Intent biometricIntent = new Intent(this, BiometricAuthActivity.class);
                    biometricIntent.putExtra(BiometricAuthActivity.EXTRA_TARGET_ACTIVITY, WalletActivity.class.getName());
                    biometricIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(biometricIntent);
                    finish();
                    return;
                } else {
                    // Biometric not enabled - restart with CLEAR_TASK to force fresh instance (like widget does)
                    final Intent freshIntent = new Intent(this, WalletActivity.class);
                    freshIntent.setAction(Intent.ACTION_MAIN);
                    freshIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    freshIntent.putExtra("already_restarted", true); // Prevent infinite loop
                    freshIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(freshIntent);
                    finish();
                    return;
                }
            }
        }
        
        // Initialize pending transaction retry service
        pendingTransactionRetryService = new PendingTransactionRetryService(this);
        
        // Initialize RadioDoge status checker
        radiodogeStatusChecker = new RadioDogeStatusChecker(this);

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        walletActivityViewModel = new ViewModelProvider(this).get(AbstractWalletActivityViewModel.class);
        viewModel = new ViewModelProvider(this).get(WalletActivityViewModel.class);

        setContentView(R.layout.wallet_activity_onepane_vertical);
        contentView = findViewById(android.R.id.content);
        levitateView = contentView.findViewWithTag("levitate");
        radiodogeStatusBanner = findViewById(R.id.radiodoge_status_banner);
        
        // Set the banner view for the status checker
        if (radiodogeStatusChecker != null) {
            radiodogeStatusChecker.setBannerView(radiodogeStatusBanner);
        }

        // Make view tagged with 'levitate' scroll away and quickly return.
        if (levitateView != null) {
            final CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(
                    levitateView.getLayoutParams().width, levitateView.getLayoutParams().height);
            layoutParams.setBehavior(new QuickReturnBehavior());
            levitateView.setLayoutParams(layoutParams);
            levitateView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                final int height = bottom - top;
                final View targetList = findViewById(R.id.wallet_transactions_list);
                targetList.setPadding(targetList.getPaddingLeft(), height, targetList.getPaddingRight(),
                        targetList.getPaddingBottom());
                final View targetEmpty = findViewById(R.id.wallet_transactions_empty);
                targetEmpty.setPadding(targetEmpty.getPaddingLeft(), height, targetEmpty.getPaddingRight(),
                        targetEmpty.getPaddingBottom());
            });
        }

        OnFirstPreDraw.listen(contentView, viewModel);
        enterAnimation = buildEnterAnimation(contentView);
        
        // Check if this is first time setup
        checkFirstTimeSetup();

        viewModel.walletEncrypted.observe(this, isEncrypted -> invalidateOptionsMenu());
        viewModel.walletLegacyFallback.observe(this, isLegacyFallback -> invalidateOptionsMenu());
        viewModel.showHelpDialog.observe(this, new Event.Observer<Integer>() {
            @Override
            protected void onEvent(final Integer messageResId) {
                HelpDialogFragment.page(getSupportFragmentManager(), messageResId);
            }
        });
        viewModel.showBackupWalletDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                BackupWalletActivity.start(WalletActivity.this);
            }
        });
        viewModel.showRestoreWalletDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                RestoreWalletDialogFragment.showPick(getSupportFragmentManager());
            }
        });
        viewModel.showEncryptKeysDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                EncryptKeysDialogFragment.show(getSupportFragmentManager());
            }
        });
        viewModel.showReportIssueDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                ReportIssueDialogFragment.show(getSupportFragmentManager(), R.string.report_issue_dialog_title_issue,
                        R.string.report_issue_dialog_message_issue, Constants.REPORT_SUBJECT_ISSUE, null);
            }
        });
        viewModel.showReportCrashDialog.observe(this, new Event.Observer<Void>() {
            @Override
            protected void onEvent(final Void v) {
                ReportIssueDialogFragment.show(getSupportFragmentManager(), R.string.report_issue_dialog_title_crash,
                        R.string.report_issue_dialog_message_crash, Constants.REPORT_SUBJECT_CRASH, null);
            }
        });
        viewModel.enterAnimation.observe(this, state -> {
            if (state == WalletActivityViewModel.EnterAnimationState.WAITING) {
                // API level 26: enterAnimation.setCurrentPlayTime(0);
                for (final Animator animation : enterAnimation.getChildAnimations())
                    ((ValueAnimator) animation).setCurrentPlayTime(0);
            } else if (state == WalletActivityViewModel.EnterAnimationState.ANIMATING) {
                reportFullyDrawn();
                enterAnimation.start();
                enterAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        viewModel.animationFinished();
                    }
                });
            } else if (state == WalletActivityViewModel.EnterAnimationState.FINISHED) {
                getWindow().getDecorView().setBackground(null);
            }
        });
        if (savedInstanceState == null)
            viewModel.animateWhenLoadingFinished();
        else
            viewModel.animationFinished();

        final View exchangeRatesFragment = findViewById(R.id.wallet_main_twopanes_exchange_rates);
        if (exchangeRatesFragment != null)
            exchangeRatesFragment.setVisibility(Constants.ENABLE_EXCHANGE_RATES ? View.VISIBLE : View.GONE);

        if (savedInstanceState == null && CrashReporter.hasSavedCrashTrace())
            viewModel.showReportCrashDialog.setValue(Event.simple());

        config.touchLastUsed();

        handleIntent(getIntent());

        // Only add fragments if they don't already exist
        // This prevents duplication when app returns from background or activity is recreated
        final FragmentManager fragmentManager = getSupportFragmentManager();
        
        // Check if fragments already exist before adding to prevent duplicates
        Fragment maybeMaintenanceFragment = fragmentManager.findFragmentByTag(MaybeMaintenanceFragment.class.getName());
        Fragment alertDialogsFragment = fragmentManager.findFragmentByTag(AlertDialogsFragment.class.getName());
        
        if (maybeMaintenanceFragment == null) {
            MaybeMaintenanceFragment.add(fragmentManager);
        }
        if (alertDialogsFragment == null) {
            AlertDialogsFragment.add(fragmentManager);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if terminal mode is enabled and redirect
        final Configuration config = application.getConfiguration();
        if (config.getPaymentTerminalEnabled() && !getClass().equals(PaymentTerminalActivity.class)) {
            final Intent intent = new Intent(this, PaymentTerminalActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Simplified biometric check - only check if biometric is required
        if (BiometricHelper.isBiometricRequired(this)) {
            showBiometricAuthentication();
        } else {
            // User is already authenticated or biometric is disabled
            startBlockchainService();
            
            // Check for pending transactions that can be retried via RadioDoge
            handler.postDelayed(() -> {
                if (pendingTransactionRetryService != null) {
                    pendingTransactionRetryService.retryPendingTransactions();
                }
                
                // Start RadioDoge status checking
                if (radiodogeStatusChecker != null) {
                    radiodogeStatusChecker.startChecking();
                }
            }, 100);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        
        // Stop RadioDoge status checking when paused
        if (radiodogeStatusChecker != null) {
            radiodogeStatusChecker.stopChecking();
        }
        
        // Clear biometric authentication when app goes to background
        BiometricHelper.setAuthenticated(this, false);
        
        super.onPause();
    }

    private void startBlockchainService() {
        handler.postDelayed(() -> {
            // delayed start so that UI has enough time to initialize
            BlockchainService.start(WalletActivity.this, true);
            
            // Also schedule recurring payments service
            RecurringPaymentsService.schedule(application);
        }, 200); // Reduced delay from 1000ms to 200ms
    }

    private void showBiometricAuthentication() {
        // Ensure no existing biometric dialog is shown
        Fragment existingFragment = getSupportFragmentManager().findFragmentByTag(BiometricAuthDialogFragment.class.getName());
        if (existingFragment != null) {
            return; // Dialog already exists, don't show another
        }
        
        BiometricAuthDialogFragment.show(getSupportFragmentManager(), new BiometricAuthDialogFragment.BiometricAuthCallback() {
            @Override
            public void onBiometricAuthSuccess() {
                // Authentication successful, mark as authenticated and start service
                BiometricHelper.setAuthenticated(WalletActivity.this, true);
                startBlockchainService();
            }

            @Override
            public void onBiometricAuthError(String error) {
                // Show error and exit app
                android.widget.Toast.makeText(WalletActivity.this, error, android.widget.Toast.LENGTH_LONG).show();
                // Exit the app on error
                finishAffinity();
            }

            @Override
            public void onBiometricAuthCancelled() {
                // User cancelled, exit the app
                android.widget.Toast.makeText(WalletActivity.this, "Authentication required to access wallet", android.widget.Toast.LENGTH_SHORT).show();
                finishAffinity();
            }
        });
    }


    /**
     * Check RadioDoge logs for transaction confirmation
     */
    public void checkTransactionConfirmation(String transactionId) {
        RadioDogeLogChecker.checkTransactionConfirmation(transactionId, new RadioDogeLogChecker.RadioDogeLogCallback() {
            @Override
            public void onTransactionConfirmed(String txId, String confirmationResult) {
                log.info("Transaction confirmed via RadioDoge: {} -> {}", txId, confirmationResult);
                // Update UI to show transaction is confirmed and navigate back to main page
                handler.post(() -> {
                    android.widget.Toast.makeText(WalletActivity.this, 
                        "Transaction confirmed via RadioDoge: " + confirmationResult, 
                        android.widget.Toast.LENGTH_LONG).show();
                    
                    // Navigate back to the main wallet page (transactions list)
                    // This will close any send dialog and return to the main view
                    finish();
                });
            }

            @Override
            public void onTransactionError(String txId, String errorMessage) {
                log.warn("Transaction error via RadioDoge: {} -> {}", txId, errorMessage);
                handler.post(() -> {
                    android.widget.Toast.makeText(WalletActivity.this, 
                        "Transaction error via RadioDoge: " + errorMessage, 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onLogCheckFailed(String error) {
                log.warn("Failed to check RadioDoge logs: {}", error);
            }
        });
    }

    private AnimatorSet buildEnterAnimation(final View contentView) {
        final Drawable background = getWindow().getDecorView().getBackground();
        final int duration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
        final Animator splashBackgroundFadeOut = AnimatorInflater.loadAnimator(WalletActivity.this, R.animator.fade_out_drawable);
        final Animator splashForegroundFadeOut = AnimatorInflater.loadAnimator(WalletActivity.this, R.animator.fade_out_drawable);
        splashBackgroundFadeOut.setTarget(((LayerDrawable) background).getDrawable(1));
        splashForegroundFadeOut.setTarget(((LayerDrawable) background).getDrawable(2));
        final AnimatorSet fragmentEnterAnimation = new AnimatorSet();
        final AnimatorSet.Builder fragmentEnterAnimationBuilder =
                fragmentEnterAnimation.play(splashBackgroundFadeOut).with(splashForegroundFadeOut);

        final View slideInLeftView = contentView.findViewWithTag("slide_in_left");
        if (slideInLeftView != null) {
            final ValueAnimator slide = ValueAnimator.ofFloat(-1.0f, 0.0f);
            slide.addUpdateListener(animator -> {
                float animatedValue = (float) animator.getAnimatedValue();
                slideInLeftView.setTranslationX(
                        animatedValue * (slideInLeftView.getWidth() + slideInLeftView.getPaddingLeft()));
            });
            slide.setInterpolator(new DecelerateInterpolator());
            slide.setDuration(duration);
            slide.setTarget(slideInLeftView);
            final Animator fadeIn = AnimatorInflater.loadAnimator(WalletActivity.this, R.animator.fade_in_view);
            fadeIn.setTarget(slideInLeftView);
            fragmentEnterAnimationBuilder.before(slide).before(fadeIn);
        }

        final View slideInRightView = contentView.findViewWithTag("slide_in_right");
        if (slideInRightView != null) {
            final ValueAnimator slide = ValueAnimator.ofFloat(1.0f, 0.0f);
            slide.addUpdateListener(animator -> {
                float animatedValue = (float) animator.getAnimatedValue();
                slideInRightView.setTranslationX(
                        animatedValue * (slideInRightView.getWidth() + slideInRightView.getPaddingRight()));
            });
            slide.setInterpolator(new DecelerateInterpolator());
            slide.setDuration(duration);
            slide.setTarget(slideInRightView);
            final Animator fadeIn = AnimatorInflater.loadAnimator(WalletActivity.this, R.animator.fade_in_view);
            fadeIn.setTarget(slideInRightView);
            fragmentEnterAnimationBuilder.before(slide).before(fadeIn);
        }

        final View slideInTopView = contentView.findViewWithTag("slide_in_top");
        if (slideInTopView != null) {
            final ValueAnimator slide = ValueAnimator.ofFloat(-1.0f, 0.0f);
            slide.addUpdateListener(animator -> {
                float animatedValue = (float) animator.getAnimatedValue();
                slideInTopView.setTranslationY(
                        animatedValue * (slideInTopView.getHeight() + slideInTopView.getPaddingTop()));
            });
            slide.setInterpolator(new DecelerateInterpolator());
            slide.setDuration(duration);
            slide.setTarget(slideInTopView);
            final Animator fadeIn = AnimatorInflater.loadAnimator(WalletActivity.this, R.animator.fade_in_view);
            fadeIn.setTarget(slideInTopView);
            fragmentEnterAnimationBuilder.before(slide).before(fadeIn);
        }

        final View slideInBottomView = contentView.findViewWithTag("slide_in_bottom");
        if (slideInBottomView != null) {
            final ValueAnimator slide = ValueAnimator.ofFloat(1.0f, 0.0f);
            slide.addUpdateListener(animator -> {
                float animatedValue = (float) animator.getAnimatedValue();
                slideInBottomView.setTranslationY(
                        animatedValue * (slideInBottomView.getHeight() + slideInBottomView.getPaddingBottom()));
            });
            slide.setInterpolator(new DecelerateInterpolator());
            slide.setDuration(duration);
            slide.setTarget(slideInBottomView);
            final Animator fadeIn = AnimatorInflater.loadAnimator(WalletActivity.this, R.animator.fade_in_view);
            fadeIn.setTarget(slideInBottomView);
            fragmentEnterAnimationBuilder.before(slide).before(fadeIn);
        }

        if (levitateView != null) {
            final ObjectAnimator elevate = ObjectAnimator.ofFloat(levitateView, "elevation", 0.0f,
                    levitateView.getElevation());
            elevate.setDuration(duration);
            fragmentEnterAnimationBuilder.before(elevate);
            final Drawable levitateBackground = levitateView.getBackground();
            final Animator fadeIn = AnimatorInflater.loadAnimator(WalletActivity.this, R.animator.fade_in_drawable);
            fadeIn.setTarget(levitateBackground);
            fragmentEnterAnimationBuilder.before(fadeIn);
        }

        return fragmentEnterAnimation;
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        
        // Make app icon launches behave exactly like widget launches
        // If biometric is enabled, route through BiometricAuthActivity first (no sensitive data shown)
        // Otherwise, restart with CLEAR_TASK to force fresh instance for animation
        if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction()) 
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            // This is an app icon launch while activity is already running - match widget behavior
            // Skip if this intent already has the restart flag to prevent infinite loop
            if (!intent.getBooleanExtra("already_restarted", false)) {
                // Check if biometric is enabled and available (like widget does)
                if (BiometricHelper.isBiometricEnabled(this) && BiometricHelper.isBiometricAvailable(this)) {
                    // Route through BiometricAuthActivity first - no sensitive data shown before auth
                    final Intent biometricIntent = new Intent(this, BiometricAuthActivity.class);
                    biometricIntent.putExtra(BiometricAuthActivity.EXTRA_TARGET_ACTIVITY, WalletActivity.class.getName());
                    biometricIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(biometricIntent);
                    finish();
                    return;
                } else {
                    // Biometric not enabled - restart with CLEAR_TASK
                    final Intent freshIntent = new Intent(this, WalletActivity.class);
                    freshIntent.setAction(Intent.ACTION_MAIN);
                    freshIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    freshIntent.putExtra("already_restarted", true); // Prevent infinite loop
                    freshIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(freshIntent);
                    finish();
                    return;
                }
            }
        }
        
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        final String action = intent.getAction();

        // Handle report issue intent
        if (intent.getBooleanExtra(INTENT_EXTRA_SHOW_REPORT_ISSUE, false)) {
            viewModel.showReportIssueDialog.setValue(Event.simple());
            return;
        }

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            final String inputType = intent.getType();
            final NdefMessage ndefMessage = (NdefMessage) intent
                    .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
            final byte[] input = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

            new BinaryInputParser(inputType, input) {
                @Override
                protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                    cannotClassify(inputType);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    final DialogBuilder dialog = DialogBuilder.dialog(WalletActivity.this, 0, messageResId, messageArgs);
                    dialog.singleDismissButton(null);
                    dialog.show();
                }
            }.parse();
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                new StringInputParser(input) {
                    @Override
                    protected void handlePaymentIntent(final PaymentIntent paymentIntent) {
                        SendCoinsActivity.start(WalletActivity.this, paymentIntent);
                    }

                    @Override
                    protected void handlePrivateKey(final PrefixedChecksummedBytes key) {
                        if (Constants.ENABLE_SWEEP_WALLET)
                            SweepWalletActivity.start(WalletActivity.this, key);
                        else
                            super.handlePrivateKey(key);
                    }

                    @Override
                    protected void handleDirectTransaction(final Transaction tx) throws VerificationException {
                        walletActivityViewModel.broadcastTransaction(tx);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        final DialogBuilder dialog = DialogBuilder.dialog(WalletActivity.this, R.string.button_scan, messageResId, messageArgs);
                        dialog.singleDismissButton(null);
                        dialog.show();
                    }
                }.parse();
            }
        } else if (requestCode == REQUEST_CODE_SCAN_CHILD_ACTIVATION) {
            if (resultCode == Activity.RESULT_OK) {
                // Handle child activation QR scan - start FamilyModeActivity with the scanned data
                final String scannedData = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                if (scannedData != null) {
                    // Start FamilyModeActivity with the scanned derived key
                    Intent familyIntent = new Intent(this, FamilyModeActivity.class);
                    familyIntent.putExtra("scanned_derived_key", scannedData);
                    startActivity(familyIntent);
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.wallet_options, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final Resources res = getResources();

        menu.findItem(R.id.wallet_options_sweep_wallet).setVisible(Constants.ENABLE_SWEEP_WALLET);
        final String externalStorageState = Environment.getExternalStorageState();
        final boolean enableRestoreWalletOption = Environment.MEDIA_MOUNTED.equals(externalStorageState)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(externalStorageState);
        menu.findItem(R.id.wallet_options_restore_wallet).setEnabled(enableRestoreWalletOption);
        final Boolean isEncrypted = viewModel.walletEncrypted.getValue();
        if (isEncrypted != null) {
            final MenuItem encryptKeysOption = menu.findItem(R.id.wallet_options_encrypt_keys);
            encryptKeysOption.setTitle(isEncrypted ? R.string.wallet_options_encrypt_keys_change
                    : R.string.wallet_options_encrypt_keys_set);
            encryptKeysOption.setVisible(true);
        }
        final Boolean isLegacyFallback = viewModel.walletLegacyFallback.getValue();
        if (isLegacyFallback != null) {
            final MenuItem requestLegacyOption = menu.findItem(R.id.wallet_options_request_legacy);
            requestLegacyOption.setVisible(isLegacyFallback);
        }

        // Check if child mode is active
        boolean isChildModeActive = de.schildbach.wallet.util.ChildModeHelper.isChildModeActive(this);
        
        // Hide "Activate Child" if children already exist OR if child mode is active
        try {
            FamilyMemberDatabase familyDatabase = new FamilyMemberDatabase(this);
            boolean hasChildren = !familyDatabase.getAllFamilyMembers().isEmpty();
            final MenuItem activateChildOption = menu.findItem(R.id.wallet_options_activate_child);
            if (activateChildOption != null) {
                activateChildOption.setVisible(!hasChildren && !isChildModeActive);
            }
            
            // Keep "Family Mode" visible but it will require PIN protection when child mode is active
            // The PIN protection is handled in onOptionsItemSelected
            
        } catch (Exception e) {
            // If there's an error checking for children, show the option
            final MenuItem activateChildOption = menu.findItem(R.id.wallet_options_activate_child);
            if (activateChildOption != null) {
                activateChildOption.setVisible(!isChildModeActive);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int itemId = item.getItemId();
        
        // Check if child mode is active and PIN protection is needed
        boolean isChildModeActive = de.schildbach.wallet.util.ChildModeHelper.isChildModeActive(this);
        boolean needsPinProtection = isChildModeActive && (itemId == R.id.wallet_options_safety || 
                                                          itemId == R.id.wallet_options_family_mode || 
                                                          itemId == R.id.wallet_options_preferences ||
                                                          itemId == R.id.wallet_options_encrypt_keys ||
                                                          itemId == R.id.wallet_options_backup_wallet ||
                                                          itemId == R.id.wallet_options_restore_wallet ||
                                                          itemId == R.id.wallet_options_sweep_wallet);
        
        if (needsPinProtection) {
            showPinProtectionDialog(itemId);
            return true;
        }
        
        if (itemId == R.id.wallet_options_request) {
            handleRequestCoins();
            return true;
        } else if (itemId == R.id.wallet_options_request_legacy) {
            RequestCoinsActivity.start(this, Script.ScriptType.P2PKH);
            return true;
        } else if (itemId == R.id.wallet_options_send) {
            handleSendCoins();
            return true;
        } else if (itemId == R.id.wallet_options_scan) {
            handleScan(null);
            return true;
        } else if (itemId == R.id.wallet_options_address_book) {
            AddressBookActivity.start(this);
            return true;
        } else if (itemId == R.id.wallet_options_family_mode) {
            startActivity(new Intent(this, FamilyModeActivity.class));
            return true;
        } else if (itemId == R.id.wallet_options_activate_child) {
            // Start QR scanner for child activation - use the same scanner as FamilyModeActivity
            ScanActivity.startForResult(this, REQUEST_CODE_SCAN_CHILD_ACTIVATION);
            return true;
        } else if (itemId == R.id.wallet_options_recurring_payments) {
            startActivity(new Intent(this, RecurringPaymentsActivity.class));
            return true;
        } else if (itemId == R.id.wallet_options_digital_signature) {
            startActivity(new Intent(this, DigitalSignatureActivity.class));
            return true;
        } else if (itemId == R.id.wallet_options_sweep_wallet) {
            SweepWalletActivity.start(this);
            return true;
        } else if (itemId == R.id.wallet_options_accounting_reports) {
            startActivity(new Intent(this, AccountingReportsActivity.class));
            return true;
        } else if (itemId == R.id.wallet_options_restore_wallet) {
            viewModel.showRestoreWalletDialog.setValue(Event.simple());
            return true;
        } else if (itemId == R.id.wallet_options_backup_wallet) {
            viewModel.showBackupWalletDialog.setValue(Event.simple());
            return true;
        } else if (itemId == R.id.wallet_options_encrypt_keys) {
            viewModel.showEncryptKeysDialog.setValue(Event.simple());
            return true;
        } else if (itemId == R.id.wallet_options_preferences) {
            startActivity(new Intent(this, PreferenceActivity.class));
            return true;
        } else if (itemId == R.id.wallet_options_safety) {
            viewModel.showHelpDialog.setValue(new Event<>(R.string.help_safety));
            return true;
        } else if (itemId == R.id.wallet_options_technical_notes) {
            viewModel.showHelpDialog.setValue(new Event<>(R.string.help_technical_notes));
            return true;
        } else if (itemId == R.id.wallet_options_education) {
            Intent intent = new Intent(this, EducationActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.wallet_options_help) {
            // Open the website documentation instead of showing help dialog
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://dogecoinwallet.org/#documentation"));
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void handleRequestCoins() {
        RequestCoinsActivity.start(this);
    }
    
    private void showPinProtectionDialog(int menuItemId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("PIN Required");
        builder.setMessage("Please enter the PIN to access this feature:");
        
        final EditText pinInput = new EditText(this);
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Enter PIN");
        builder.setView(pinInput);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            String enteredPin = pinInput.getText().toString();
            // For now, we'll use a simple PIN check (you can implement proper PIN validation)
            if (validatePin(enteredPin)) {
                // PIN is correct, proceed with the original action
                handleMenuAction(menuItemId);
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton(R.string.common_cancel, null);
        builder.show();
    }
    
    private boolean validatePin(String enteredPin) {
        // Get the stored PIN from SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("child_mode", android.content.Context.MODE_PRIVATE);
        String storedPin = prefs.getString("child_mode_pin", null);
        
        if (storedPin == null) {
            // No PIN set, allow access (shouldn't happen in child mode)
            return true;
        }
        
        return storedPin.equals(enteredPin);
    }
    
    private void handleMenuAction(int menuItemId) {
        if (menuItemId == R.id.wallet_options_safety) {
            viewModel.showHelpDialog.setValue(new Event<>(R.string.help_safety));
        } else if (menuItemId == R.id.wallet_options_family_mode) {
            startActivity(new Intent(this, FamilyModeActivity.class));
        } else if (menuItemId == R.id.wallet_options_preferences) {
            startActivity(new Intent(this, PreferenceActivity.class));
        } else if (menuItemId == R.id.wallet_options_encrypt_keys) {
            viewModel.showEncryptKeysDialog.setValue(Event.simple());
        } else if (menuItemId == R.id.wallet_options_backup_wallet) {
            viewModel.showBackupWalletDialog.setValue(Event.simple());
        } else if (menuItemId == R.id.wallet_options_restore_wallet) {
            viewModel.showRestoreWalletDialog.setValue(Event.simple());
        } else if (menuItemId == R.id.wallet_options_sweep_wallet) {
            SweepWalletActivity.start(this);
        }
    }

    public void handleSendCoins() {
        startActivity(new Intent(this, SendCoinsActivity.class));
    }

    public void handleScan(final View clickView) {
        // The animation must be ended because of several graphical glitching that happens when the
        // Camera/SurfaceView is used while the animation is running.
        enterAnimation.end();
        ScanActivity.startForResult(this, clickView, WalletActivity.REQUEST_CODE_SCAN);
    }

    private static final class QuickReturnBehavior extends CoordinatorLayout.Behavior<View> {
        @Override
        public boolean onStartNestedScroll(final CoordinatorLayout coordinatorLayout, final View child,
                final View directTargetChild, final View target, final int nestedScrollAxes, final int type) {
            return (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        }

        @Override
        public void onNestedScroll(final CoordinatorLayout coordinatorLayout, final View child, final View target,
                final int dxConsumed, final int dyConsumed, final int dxUnconsumed, final int dyUnconsumed,
                final int type) {
            child.setTranslationY(Floats.constrainToRange(child.getTranslationY() - dyConsumed, -child.getHeight(), 0));
        }
    }
    
    /**
     * Check if this is the first time the app is launched and show setup dialog
     */
    private void checkFirstTimeSetup() {
        // Check if wallet file exists - if not, this is first time setup
        final File walletFile = getFileStreamPath(Constants.Files.WALLET_FILENAME_PROTOBUF);
        if (!walletFile.exists()) {
            FirstTimeSetupDialogFragment.show(getSupportFragmentManager(), new FirstTimeSetupDialogFragment.OnSetupChoiceListener() {
                @Override
                public void onNewWallet() {
                    // Create a new wallet and force backup
                    // The wallet will be created automatically when first accessed
                    // After creation, we'll force the user to create a backup
                    showNewWalletBackupDialog();
                }
                
                @Override
                public void onRestoreWallet() {
                    // Show restore wallet dialog
                    RestoreWalletDialogFragment.showPick(getSupportFragmentManager());
                }
                
                @Override
                public void onActivateChildWallet() {
                    // Navigate to Family Mode to activate child wallet
                    final Intent intent = new Intent(WalletActivity.this, FamilyModeActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    private void showNewWalletBackupDialog() {
        // Show a dialog explaining that backup is required for new wallets
        final DialogBuilder builder = new DialogBuilder(this);
        builder.setTitle(R.string.new_wallet_backup_required_title);
        builder.setMessage(R.string.new_wallet_backup_required_message);
        builder.setPositiveButton(R.string.new_wallet_backup_required_ok, (dialog, which) -> {
            // Start the backup process
            startBackupProcess();
        });
        builder.setNegativeButton(R.string.new_wallet_backup_required_cancel, (dialog, which) -> {
            // User cancelled - show the first time setup dialog again
            checkFirstTimeSetup();
        });
        builder.setCancelable(false); // Force user to make a choice
        builder.show();
    }

    private void startBackupProcess() {
        // Wait for wallet to be created, then show backup dialog
        // We'll use a delayed approach to ensure the wallet is ready
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Show the backup dialog
            BackupWalletDialogFragment.show(getSupportFragmentManager(), new BackupWalletDialogFragment.OnBackupCompleteListener() {
                @Override
                public void onBackupComplete(boolean success) {
                    if (success) {
                        // Backup completed successfully
                        Toast.makeText(WalletActivity.this, R.string.new_wallet_backup_success, Toast.LENGTH_LONG).show();
                    } else {
                        // Backup failed or was cancelled - show the backup dialog again
                        showNewWalletBackupDialog();
                    }
                }
            });
        }, 1000); // Wait 1 second for wallet to be created
    }

}
