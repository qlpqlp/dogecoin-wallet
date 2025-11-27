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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.BiometricHelper;
import de.schildbach.wallet.util.Bluetooth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;

/**
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public final class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener {
    private Activity activity;
    private WalletApplication application;
    private Configuration config;
    private PackageManager pm;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private EditTextPreference trustedPeerPreference;
    private Preference trustedPeerOnlyPreference;
    private Preference ownNamePreference;
    private EditTextPreference bluetoothAddressPreference;
    private android.preference.CheckBoxPreference biometricPreference;
    private android.preference.CheckBoxPreference radiodogePreference;
    private android.preference.CheckBoxPreference useDogePreference;
    private android.preference.CheckBoxPreference enableLoggingPreference;

    private static final int BLUETOOTH_ADDRESS_LENGTH = 6 * 2 + 5; // including the colons
    private static final Logger log = LoggerFactory.getLogger(SettingsFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
        this.application = (WalletApplication) activity.getApplication();
        this.config = application.getConfiguration();
        this.pm = activity.getPackageManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_settings);

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        final ListPreference syncModePreference = (ListPreference) findPreference(Configuration.PREFS_KEY_SYNC_MODE);
        syncModePreference.setEntryValues(new CharSequence[] {
                Configuration.SyncMode.CONNECTION_FILTER.name(),
                Configuration.SyncMode.FULL.name() });
        syncModePreference.setEntries(new CharSequence[] {
                Html.fromHtml(getString(R.string.preferences_sync_mode_labels_connection_filter)),
                Html.fromHtml(getString(R.string.preferences_sync_mode_labels_full)) });
        if (!application.fullSyncCapable())
            removeOrDisablePreference(syncModePreference);

        trustedPeerPreference = (EditTextPreference) findPreference(Configuration.PREFS_KEY_TRUSTED_PEERS);
        trustedPeerPreference.setOnPreferenceChangeListener(this);
        trustedPeerPreference.setDialogMessage(getString(R.string.preferences_trusted_peer_dialog_message) + "\n\n" +
                getString(R.string.preferences_trusted_peer_dialog_message_multiple));

        trustedPeerOnlyPreference = findPreference(Configuration.PREFS_KEY_TRUSTED_PEERS_ONLY);
        trustedPeerOnlyPreference.setOnPreferenceChangeListener(this);

        final Preference dataUsagePreference = findPreference(Configuration.PREFS_KEY_DATA_USAGE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            dataUsagePreference.setIntent(new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:" + application.getPackageName())));
        if (dataUsagePreference.getIntent() == null || pm.resolveActivity(dataUsagePreference.getIntent(), 0) == null)
            removeOrDisablePreference(dataUsagePreference);

        final Preference notificationsPreference = findPreference(Configuration.PREFS_KEY_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notificationsPreference.setIntent(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, application.getPackageName()));
        if (notificationsPreference.getIntent() == null || pm.resolveActivity(notificationsPreference.getIntent(), 0) == null)
            removeOrDisablePreference(notificationsPreference);

        final Preference batteryOptimizationPreference = findPreference(Configuration.PREFS_KEY_BATTERY_OPTIMIZATION);
        if (batteryOptimizationPreference != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                batteryOptimizationPreference.setIntent(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + application.getPackageName())));
            }
            if (batteryOptimizationPreference.getIntent() == null || pm.resolveActivity(batteryOptimizationPreference.getIntent(), 0) == null)
                removeOrDisablePreference(batteryOptimizationPreference);
        }

        ownNamePreference = findPreference(Configuration.PREFS_KEY_OWN_NAME);
        ownNamePreference.setOnPreferenceChangeListener(this);

        bluetoothAddressPreference = (EditTextPreference) findPreference(Configuration.PREFS_KEY_BLUETOOTH_ADDRESS);
        bluetoothAddressPreference.setOnPreferenceChangeListener(this);
        final InputFilter.AllCaps allCaps = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ?
                new InputFilter.AllCaps(Locale.US) : new InputFilter.AllCaps();
        final InputFilter.LengthFilter maxLength = new InputFilter.LengthFilter(BLUETOOTH_ADDRESS_LENGTH);
        final RestrictToHex hex = new RestrictToHex();
        bluetoothAddressPreference.getEditText().setFilters(new InputFilter[] { maxLength, allCaps, hex });
        bluetoothAddressPreference.getEditText().addTextChangedListener(colonFormat);

        // Initialize biometric preference
        biometricPreference = (android.preference.CheckBoxPreference) findPreference("biometric_enabled");
        if (biometricPreference != null) {
            // Check if biometric is available
            if (!BiometricHelper.isBiometricAvailable(activity)) {
                biometricPreference.setEnabled(false);
                biometricPreference.setSummary(R.string.biometric_not_available);
            } else {
                biometricPreference.setChecked(BiometricHelper.isBiometricEnabled(activity));
                biometricPreference.setOnPreferenceChangeListener(this);
            }
        }

        // Initialize RadioDoge preference
        radiodogePreference = (android.preference.CheckBoxPreference) findPreference(Configuration.PREFS_KEY_RADIODOGE_ENABLED);
        if (radiodogePreference != null) {
            radiodogePreference.setChecked(config.getRadioDogeEnabled());
            radiodogePreference.setOnPreferenceChangeListener(this);
        }

        // Initialize Use Doge preference
        useDogePreference = (android.preference.CheckBoxPreference) findPreference(Configuration.PREFS_KEY_USE_DOGE_ENABLED);
        if (useDogePreference != null) {
            useDogePreference.setChecked(config.getUseDogeEnabled());
            useDogePreference.setOnPreferenceChangeListener(this);
        }

        // Initialize Enable Logging preference
        enableLoggingPreference = (android.preference.CheckBoxPreference) findPreference(Configuration.PREFS_KEY_ENABLE_LOGGING);
        if (enableLoggingPreference != null) {
            enableLoggingPreference.setChecked(config.getEnableLogging());
            enableLoggingPreference.setOnPreferenceChangeListener(this);
        }

        // Initialize Payment Terminal Mode preference
        final Preference paymentTerminalPreference = findPreference("payment_terminal_mode");
        if (paymentTerminalPreference != null) {
            updatePaymentTerminalSummary(paymentTerminalPreference);
            paymentTerminalPreference.setOnPreferenceClickListener(preference -> {
                handlePaymentTerminalPreferenceClick();
                return true;
            });
        }

        updateTrustedPeer();
        updateOwnName();
        updateBluetoothAddress();
    }

    @Override
    public void onDestroy() {
        bluetoothAddressPreference.getEditText().removeTextChangedListener(colonFormat);
        bluetoothAddressPreference.setOnPreferenceChangeListener(null);
        ownNamePreference.setOnPreferenceChangeListener(null);
        trustedPeerOnlyPreference.setOnPreferenceChangeListener(null);
        trustedPeerPreference.setOnPreferenceChangeListener(null);
        if (biometricPreference != null) {
            biometricPreference.setOnPreferenceChangeListener(null);
        }
        if (radiodogePreference != null) {
            radiodogePreference.setOnPreferenceChangeListener(null);
        }
        if (useDogePreference != null) {
            useDogePreference.setOnPreferenceChangeListener(null);
        }
        if (enableLoggingPreference != null) {
            enableLoggingPreference.setOnPreferenceChangeListener(null);
        }

        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        // delay action because preference isn't persisted until after this method returns
        handler.post(() -> {
            if (preference.equals(trustedPeerPreference))
                updateTrustedPeer();
            else if (preference.equals(ownNamePreference))
                updateOwnName();
            else if (preference.equals(bluetoothAddressPreference))
                updateBluetoothAddress();
            else if (preference.equals(biometricPreference))
                updateBiometricPreference((Boolean) newValue);
            else if (preference.equals(radiodogePreference))
                updateRadioDogePreference((Boolean) newValue);
            else if (preference.equals(useDogePreference))
                config.setUseDogeEnabled((Boolean) newValue);
            else if (preference.equals(enableLoggingPreference))
                updateEnableLoggingPreference((Boolean) newValue);
        });
        return true;
    }

    private void updateTrustedPeer() {
        final Set<HostAndPort> trustedPeers = config.getTrustedPeers();
        if (trustedPeers.isEmpty()) {
            trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_summary);
            trustedPeerOnlyPreference.setEnabled(false);
        } else {
            trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_resolve_progress);
            trustedPeerOnlyPreference.setEnabled(true);

            for (final HostAndPort trustedPeer : trustedPeers) {
                new ResolveDnsTask(backgroundHandler) {
                    @Override
                    protected void onSuccess(final HostAndPort hostAndPort, final InetSocketAddress socketAddress) {
                        appendToTrustedPeerSummary(Constants.CHAR_CHECKMARK + " " + hostAndPort);
                        log.info("trusted peer '{}' resolved to {}", hostAndPort,
                                socketAddress.getAddress().getHostAddress());
                    }

                    @Override
                    protected void onUnknownHost(final HostAndPort hostAndPort) {
                        appendToTrustedPeerSummary(Constants.CHAR_CROSSMARK + " " + hostAndPort + " – " +
                                getString(R.string.preferences_trusted_peer_resolve_unknown_host));
                        log.info("trusted peer '{}' unknown host", hostAndPort);
                    }
                }.resolve(trustedPeer);
            }
        }
    }

    private void appendToTrustedPeerSummary(final String line) {
        // This is a hack, because we're too lazy to implement a sophisticated UI here.
        synchronized (trustedPeerPreference) {
            CharSequence summary = trustedPeerPreference.getSummary();
            if (summary.equals(getString(R.string.preferences_trusted_peer_resolve_progress)))
                summary = "";
            else
                summary = summary + "\n";
            trustedPeerPreference.setSummary(summary + line);
        }
    }

    private void updateOwnName() {
        final String ownName = config.getOwnName();
        ownNamePreference.setSummary(ownName != null ? ownName : getText(R.string.preferences_own_name_summary));
    }

    private void updateBluetoothAddress() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            String bluetoothAddress = null;
            try {
                bluetoothAddress = Bluetooth.getAddress(bluetoothAdapter);
            } catch (SecurityException e) {
                log.info("Bluetooth permission not granted, skipping Bluetooth address retrieval", e);
            }
            if (bluetoothAddress == null)
                bluetoothAddress = config.getLastBluetoothAddress();
            if (bluetoothAddress != null) {
                bluetoothAddressPreference.setSummary(bluetoothAddress);
                bluetoothAddressPreference.setEnabled(false);
            } else {
                bluetoothAddress = config.getBluetoothAddress();
                if (bluetoothAddress != null) {
                    final String normalizedBluetoothAddress =
                            Bluetooth.decompressMac(Bluetooth.compressMac(bluetoothAddress));
                    bluetoothAddressPreference.setSummary(normalizedBluetoothAddress);
                }
            }
        } else {
            removeOrDisablePreference(bluetoothAddressPreference);
        }
    }

    private void updateBiometricPreference(boolean enabled) {
        if (enabled) {
            // Enable biometric authentication
            BiometricHelper.setBiometricEnabled(activity, true);
            BiometricHelper.setBiometricSetup(activity, true);
            android.widget.Toast.makeText(activity, R.string.biometric_enable, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            // Disable biometric authentication
            BiometricHelper.setBiometricEnabled(activity, false);
            BiometricHelper.setBiometricSetup(activity, false);
            android.widget.Toast.makeText(activity, R.string.biometric_disable, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRadioDogePreference(boolean enabled) {
        config.setRadioDogeEnabled(enabled);
        if (enabled) {
            android.widget.Toast.makeText(activity, "RadioDoge support enabled", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(activity, "RadioDoge support disabled", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEnableLoggingPreference(boolean enabled) {
        config.setEnableLogging(enabled);
        // Update log level immediately
        de.schildbach.wallet.Logging.updateLogLevel(activity);
        if (enabled) {
            android.widget.Toast.makeText(activity, "Logging enabled", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(activity, "Logging disabled", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePaymentTerminalSummary(final Preference preference) {
        if (config.getPaymentTerminalEnabled()) {
            preference.setSummary(R.string.payment_terminal_active);
        } else {
            preference.setSummary(R.string.payment_terminal_inactive);
        }
    }

    private void handlePaymentTerminalPreferenceClick() {
        if (config.getPaymentTerminalEnabled()) {
            // Terminal mode is enabled, prompt for PIN to disable
            showPinDialogToDisable();
        } else {
            // Terminal mode is disabled, always prompt for a new PIN when activating
            showSetPinDialog();
        }
    }

    private void showPinDialogToDisable() {
        final android.widget.EditText editText = new android.widget.EditText(activity);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        editText.setMaxLines(1);

        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.payment_terminal_enter_pin_title)
                .setMessage(R.string.payment_terminal_enter_pin_message)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String enteredPin = editText.getText().toString();
                    String savedPin = config.getPaymentTerminalPin();
                    if (savedPin != null && savedPin.equals(enteredPin)) {
                        config.setPaymentTerminalEnabled(false);
                        // Clear the PIN when disabling
                        config.setPaymentTerminalPin(null);
                        android.widget.Toast.makeText(activity, "Terminal mode disabled", android.widget.Toast.LENGTH_SHORT).show();
                        updatePaymentTerminalSummary(findPreference("payment_terminal_mode"));
                    } else {
                        android.widget.Toast.makeText(activity, R.string.payment_terminal_wrong_pin, android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSetPinDialog() {
        final android.widget.EditText editText = new android.widget.EditText(activity);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        editText.setMaxLines(1);

        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.payment_terminal_set_pin_title)
                .setMessage(R.string.payment_terminal_set_pin_message)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String pin = editText.getText().toString();
                    if (pin != null && !pin.isEmpty() && pin.length() >= 4) {
                        config.setPaymentTerminalPin(pin);
                        activateTerminalMode();
                    } else {
                        android.widget.Toast.makeText(activity, "PIN must be at least 4 digits", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void activateTerminalMode() {
        config.setPaymentTerminalEnabled(true);
        android.widget.Toast.makeText(activity, "Terminal mode activated", android.widget.Toast.LENGTH_SHORT).show();
        updatePaymentTerminalSummary(findPreference("payment_terminal_mode"));
        
        // Start the PaymentTerminalActivity
        final Intent intent = new Intent(activity, de.schildbach.wallet.ui.PaymentTerminalActivity.class);
        startActivity(intent);
    }

    private void removeOrDisablePreference(final Preference preference) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            preference.getParent().removePreference(preference);
        else
            preference.setEnabled(false);
    }

    private static class RestrictToHex implements InputFilter {
        @Override
        public CharSequence filter(final CharSequence source, final int start, final int end, final Spanned dest,
                                   final int dstart, final int dend) {
            final StringBuilder result = new StringBuilder();
            for (int i = start; i < end; i++) {
                final char c = source.charAt(i);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':')
                    result.append(c);
            }
            return result;
        }
    }

    private final TextWatcher colonFormat = new TextWatcher() {
        private boolean inFlight = false;

        @Override
        public void afterTextChanged(final Editable s) {
            if (inFlight)
                return;

            inFlight = true;
            for (int i = 0; i < s.length(); i++) {
                final boolean atColon = i % 3 == 2;
                final char c = s.charAt(i);
                if (atColon) {
                    if (c != ':')
                        s.insert(i, ":");
                } else {
                    if (c == ':')
                        s.delete(i, i + 1);
                }
            }
            inFlight = false;
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }
    };
}
