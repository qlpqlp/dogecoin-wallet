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

package de.schildbach.wallet;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.net.Uri;
import android.text.format.DateUtils;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import de.schildbach.wallet.util.Formats;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class Configuration {
    public final int lastVersionCode;

    private final SharedPreferences prefs;
    private final Resources res;

    public static final String PREFS_KEY_BTC_PRECISION = "btc_precision";
    public static final String PREFS_KEY_OWN_NAME = "own_name";
    public static final String PREFS_KEY_SEND_COINS_AUTOCLOSE = "send_coins_autoclose";
    public static final String PREFS_KEY_EXCHANGE_CURRENCY = "exchange_currency";
    public static final String PREFS_KEY_SYNC_MODE = "sync_mode";
    public static final String PREFS_KEY_TRUSTED_PEERS = "trusted_peer";
    public static final String PREFS_KEY_TRUSTED_PEERS_ONLY = "trusted_peer_only";
    public static final String PREFS_KEY_BLOCK_EXPLORER = "block_explorer";
    public static final String PREFS_KEY_DATA_USAGE = "data_usage";
    public static final String PREFS_KEY_NOTIFICATIONS = "notifications";
    public static final String PREFS_KEY_BATTERY_OPTIMIZATION = "battery_optimization";
    public static final String PREFS_KEY_REMIND_BALANCE = "remind_balance";
    public static final String PREFS_KEY_DISCLAIMER = "disclaimer";
    public static final String PREFS_KEY_BLUETOOTH_ADDRESS = "bluetooth_address";
    public static final String PREFS_KEY_RADIODOGE_ENABLED = "radiodoge_enabled";
    public static final String PREFS_KEY_ENABLE_LOGGING = "enable_logging";
    public static final String PREFS_KEY_PAYMENT_TERMINAL_ENABLED = "payment_terminal_enabled";
    public static final String PREFS_KEY_PAYMENT_TERMINAL_PIN = "payment_terminal_pin";
    public static final String PREFS_KEY_POINT_OF_SALE_MODE = "point_of_sale_mode";
    public static final String PREFS_KEY_POS_BACKGROUND_SERVICE = "pos_background_service";
    public static final String PREFS_KEY_SHOW_FAMILY_MODE_MENU = "show_family_mode_menu";
    public static final String PREFS_KEY_SHOW_RECURRING_PAYMENTS_MENU = "show_recurring_payments_menu";
    public static final String PREFS_KEY_SHOW_DIGITAL_SIGNATURE_MENU = "show_digital_signature_menu";
    public static final String PREFS_KEY_SHOW_WRITE_CHECK_MENU = "show_write_check_menu";
    public static final String PREFS_KEY_SHOW_MULTISIG_MENU = "show_multisig_menu";
    public static final String PREFS_KEY_SHOW_AUTHENTICATION_MENU = "show_authentication_menu";
    public static final String PREFS_KEY_EXCHANGE_API_HOST = "exchange_api_host";
    public static final String PREFS_KEY_EXCHANGE_ENVIRONMENT = "exchange_environment";
    public static final String PREFS_KEY_USE_DOGE_ENABLED = "use_doge_enabled";
    public static final String PREFS_KEY_DEFAULT_FEE_CATEGORY = "default_fee_category";
    public static final String PREFS_KEY_LABS_EXCHANGE_ENABLED = "labs_exchange_enabled";
    public static final String PREFS_KEY_LABS_ATOMIC_SWAP_ENABLED = "labs_atomic_swap_enabled";

    private static final String PREFS_KEY_LAST_VERSION = "last_version";
    private static final String PREFS_KEY_LAST_USED = "last_used";
    private static final String PREFS_KEY_BEST_CHAIN_HEIGHT_EVER = "best_chain_height_ever";
    private static final String PREFS_KEY_LAST_EXCHANGE_DIRECTION = "last_exchange_direction";
    private static final String PREFS_KEY_CHANGE_LOG_VERSION = "change_log_version";
    public static final String PREFS_KEY_REMIND_BACKUP = "remind_backup";
    private static final String PREFS_KEY_LAST_BACKUP = "last_backup";
    private static final String PREFS_KEY_LAST_RESTORE = "last_restore";
    private static final String PREFS_KEY_LAST_ENCRYPT_KEYS = "last_encrypt_keys";
    private static final String PREFS_KEY_LAST_BLOCKCHAIN_RESET = "last_blockchain_reset";
    private static final String PREFS_KEY_LAST_BLUETOOTH_ADDRESS = "last_bluetooth_address";

    private static final int PREFS_DEFAULT_BTC_SHIFT = 0;
    private static final int PREFS_DEFAULT_BTC_PRECISION = 4;

    private static final Logger log = LoggerFactory.getLogger(Configuration.class);

    public Configuration(final SharedPreferences prefs, final Resources res) {
        this.prefs = prefs;
        this.res = res;

        this.lastVersionCode = prefs.getInt(PREFS_KEY_LAST_VERSION, 0);
    }

    private int getBtcPrecision() {
        final String precision = prefs.getString(PREFS_KEY_BTC_PRECISION, null);
        if (precision != null)
            return precision.charAt(0) - '0';
        else
            return PREFS_DEFAULT_BTC_PRECISION;
    }

    public int getBtcShift() {
        final String precision = prefs.getString(PREFS_KEY_BTC_PRECISION, null);
        if (precision != null)
            return precision.length() == 3 ? precision.charAt(2) - '0' : 0;
        else
            return PREFS_DEFAULT_BTC_SHIFT;
    }

    public Coin getBtcBase() {
        final int shift = getBtcShift();
        if (shift == 0)
            return Coin.COIN;
        else if (shift == 3)
            return Coin.MILLICOIN;
        else if (shift == 6)
            return Coin.MICROCOIN;
        else
            throw new IllegalStateException("cannot handle shift: " + shift);
    }

    public MonetaryFormat getFormat() {
        final int shift = getBtcShift();
        final int minPrecision = shift <= 3 ? 2 : 0;
        final int decimalRepetitions = (getBtcPrecision() - minPrecision) / 2;
        return new MonetaryFormat()
                .shift(shift)
                .minDecimals(minPrecision)
                .repeatOptionalDecimals(2, decimalRepetitions)
                .code(0, "DOGE")
                .code(3, "mDOGE")
                .code(6, "µDOGE");
    }

    public MonetaryFormat getMaxPrecisionFormat() {
        final int shift = getBtcShift();
        if (shift == 0)
            return new MonetaryFormat().shift(0).minDecimals(2).optionalDecimals(2, 2, 2);
        else if (shift == 3)
            return new MonetaryFormat().shift(3).minDecimals(2).optionalDecimals(2, 1);
        else
            return new MonetaryFormat().shift(6).minDecimals(0).optionalDecimals(2);
    }

    public String getOwnName() {
        return Strings.emptyToNull(prefs.getString(PREFS_KEY_OWN_NAME, "").trim());
    }

    public boolean getSendCoinsAutoclose() {
        return prefs.getBoolean(PREFS_KEY_SEND_COINS_AUTOCLOSE, true);
    }

    public SyncMode getSyncMode() {
        return SyncMode.valueOf(prefs.getString(PREFS_KEY_SYNC_MODE, SyncMode.CONNECTION_FILTER.name()));
    }

    public enum SyncMode {
        CONNECTION_FILTER,
        FULL
    }

    public Set<HostAndPort> getTrustedPeers() {
        final String trustedPeersStr = prefs.getString(PREFS_KEY_TRUSTED_PEERS, "");
        final Set<HostAndPort> trustedPeers = new HashSet<>();
        for (final String trustedPeer :
                Splitter.on(Formats.PATTERN_WHITESPACE).trimResults().omitEmptyStrings().split(trustedPeersStr)) {
            try {
                trustedPeers.add(HostAndPort.fromString(trustedPeer));
            } catch (final IllegalArgumentException x) {
                log.info("cannot parse: {}", trustedPeer);
            }
        }
        return trustedPeers;
    }

    public boolean getTrustedPeersOnly() {
        return prefs.getBoolean(PREFS_KEY_TRUSTED_PEERS_ONLY, false);
    }

    public boolean isTrustedPeersOnly() {
        return getTrustedPeers() != null && getTrustedPeersOnly();
    }

    public String getBlockExplorer() {
        return prefs.getString(PREFS_KEY_BLOCK_EXPLORER,
                res.getStringArray(R.array.preferences_block_explorer_values)[0]);
    }

    public boolean remindBalance() {
        return prefs.getBoolean(PREFS_KEY_REMIND_BALANCE, true);
    }

    public void setRemindBalance(final boolean remindBalance) {
        prefs.edit().putBoolean(PREFS_KEY_REMIND_BALANCE, remindBalance).apply();
    }

    public boolean remindBackup() {
        return prefs.getBoolean(PREFS_KEY_REMIND_BACKUP, true);
    }

    public long getLastBackupTime() {
        return prefs.getLong(PREFS_KEY_LAST_BACKUP, 0);
    }

    public void armBackupReminder() {
        prefs.edit().putBoolean(PREFS_KEY_REMIND_BACKUP, true).apply();
    }

    public void disarmBackupReminder() {
        prefs.edit().putBoolean(PREFS_KEY_REMIND_BACKUP, false)
                .putLong(PREFS_KEY_LAST_BACKUP, System.currentTimeMillis()).apply();
    }

    public long getLastRestoreTime() {
        return prefs.getLong(PREFS_KEY_LAST_RESTORE, 0);
    }

    public void updateLastRestoreTime() {
        prefs.edit().putLong(PREFS_KEY_LAST_RESTORE, System.currentTimeMillis()).apply();
    }

    public long getLastEncryptKeysTime() {
        return prefs.getLong(PREFS_KEY_LAST_ENCRYPT_KEYS, 0);
    }

    public void updateLastEncryptKeysTime() {
        prefs.edit().putLong(PREFS_KEY_LAST_ENCRYPT_KEYS, System.currentTimeMillis()).apply();
    }

    public long getLastBlockchainResetTime() {
        return prefs.getLong(PREFS_KEY_LAST_BLOCKCHAIN_RESET, 0);
    }

    public void updateLastBlockchainResetTime() {
        prefs.edit().putLong(PREFS_KEY_LAST_BLOCKCHAIN_RESET, System.currentTimeMillis()).apply();
    }

    public boolean getDisclaimerEnabled() {
        return prefs.getBoolean(PREFS_KEY_DISCLAIMER, true);
    }

    private String defaultCurrencyCode() {
        try {
            return Currency.getInstance(Locale.getDefault()).getCurrencyCode();
        } catch (final IllegalArgumentException x) {
            return null;
        }
    }

    public String getExchangeCurrencyCode() {
        return prefs.getString(PREFS_KEY_EXCHANGE_CURRENCY, defaultCurrencyCode());
    }

    public void setExchangeCurrencyCode(final String exchangeCurrencyCode) {
        prefs.edit().putString(PREFS_KEY_EXCHANGE_CURRENCY, exchangeCurrencyCode).apply();
    }

    public boolean versionCodeCrossed(final int currentVersionCode, final int triggeringVersionCode) {
        final boolean wasBelow = lastVersionCode < triggeringVersionCode;
        final boolean wasUsedBefore = lastVersionCode > 0;
        final boolean isNowAbove = currentVersionCode >= triggeringVersionCode;

        return wasUsedBefore && wasBelow && isNowAbove;
    }

    public void updateLastVersionCode(final int currentVersionCode) {
        prefs.edit().putInt(PREFS_KEY_LAST_VERSION, currentVersionCode).apply();

        if (currentVersionCode > lastVersionCode)
            log.info("detected app upgrade: " + lastVersionCode + " -> " + currentVersionCode);
        else if (currentVersionCode < lastVersionCode)
            log.warn("detected app downgrade: " + lastVersionCode + " -> " + currentVersionCode);
    }

    public boolean hasBeenUsed() {
        return prefs.contains(PREFS_KEY_LAST_USED);
    }

    public long getLastUsedAgo() {
        final long now = System.currentTimeMillis();

        return now - prefs.getLong(PREFS_KEY_LAST_USED, 0);
    }

    public void touchLastUsed() {
        final long prefsLastUsed = prefs.getLong(PREFS_KEY_LAST_USED, 0);
        final long now = System.currentTimeMillis();
        prefs.edit().putLong(PREFS_KEY_LAST_USED, now).apply();

        log.info("just being used - last used {} minutes ago", (now - prefsLastUsed) / DateUtils.MINUTE_IN_MILLIS);
    }

    public int getBestChainHeightEver() {
        return prefs.getInt(PREFS_KEY_BEST_CHAIN_HEIGHT_EVER, 0);
    }

    public void maybeIncrementBestChainHeightEver(final int bestChainHeightEver) {
        if (bestChainHeightEver > getBestChainHeightEver())
            prefs.edit().putInt(PREFS_KEY_BEST_CHAIN_HEIGHT_EVER, bestChainHeightEver).apply();
    }

    public void resetBestChainHeightEver() {
        prefs.edit().remove(PREFS_KEY_BEST_CHAIN_HEIGHT_EVER).apply();
    }

    public boolean getLastExchangeDirection() {
        return prefs.getBoolean(PREFS_KEY_LAST_EXCHANGE_DIRECTION, true);
    }

    public void setLastExchangeDirection(final boolean exchangeDirection) {
        prefs.edit().putBoolean(PREFS_KEY_LAST_EXCHANGE_DIRECTION, exchangeDirection).apply();
    }

    public boolean changeLogVersionCodeCrossed(final int currentVersionCode, final int triggeringVersionCode) {
        final int changeLogVersion = prefs.getInt(PREFS_KEY_CHANGE_LOG_VERSION, 0);

        final boolean wasBelow = changeLogVersion < triggeringVersionCode;
        final boolean wasUsedBefore = changeLogVersion > 0;
        final boolean isNowAbove = currentVersionCode >= triggeringVersionCode;

        prefs.edit().putInt(PREFS_KEY_CHANGE_LOG_VERSION, currentVersionCode).apply();

        return /* wasUsedBefore && */wasBelow && isNowAbove;
    }

    public String getLastBluetoothAddress() {
        return prefs.getString(PREFS_KEY_LAST_BLUETOOTH_ADDRESS, null);
    }

    public void updateLastBluetoothAddress(final String bluetoothAddress) {
        if (bluetoothAddress != null)
            prefs.edit().putString(PREFS_KEY_LAST_BLUETOOTH_ADDRESS, bluetoothAddress).apply();
    }

    public String getBluetoothAddress() {
        return prefs.getString(PREFS_KEY_BLUETOOTH_ADDRESS, null);
    }

    public void setBluetoothAddress(final String bluetoothAddress) {
        prefs.edit().putString(PREFS_KEY_BLUETOOTH_ADDRESS, bluetoothAddress).apply();
    }

    public boolean getRadioDogeEnabled() {
        return prefs.getBoolean(PREFS_KEY_RADIODOGE_ENABLED, false);
    }

    public void setRadioDogeEnabled(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_RADIODOGE_ENABLED, enabled).apply();
    }

    public boolean getEnableLogging() {
        return prefs.getBoolean(PREFS_KEY_ENABLE_LOGGING, false);
    }

    public void setEnableLogging(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_ENABLE_LOGGING, enabled).apply();
    }

    public boolean getPointOfSaleModeEnabled() {
        return prefs.getBoolean(PREFS_KEY_POINT_OF_SALE_MODE, false);
    }

    public void setPointOfSaleModeEnabled(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_POINT_OF_SALE_MODE, enabled).apply();
    }

    public boolean getPosBackgroundServiceEnabled() {
        return prefs.getBoolean(PREFS_KEY_POS_BACKGROUND_SERVICE, false);
    }

    public void setPosBackgroundServiceEnabled(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_POS_BACKGROUND_SERVICE, enabled).apply();
    }

    public boolean getPaymentTerminalEnabled() {
        return prefs.getBoolean(PREFS_KEY_PAYMENT_TERMINAL_ENABLED, false);
    }

    public void setPaymentTerminalEnabled(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_PAYMENT_TERMINAL_ENABLED, enabled).apply();
    }

    public String getPaymentTerminalPin() {
        return prefs.getString(PREFS_KEY_PAYMENT_TERMINAL_PIN, null);
    }

    public void setPaymentTerminalPin(final String pin) {
        prefs.edit().putString(PREFS_KEY_PAYMENT_TERMINAL_PIN, pin).apply();
    }

    public boolean getShowFamilyModeMenu() {
        return prefs.getBoolean(PREFS_KEY_SHOW_FAMILY_MODE_MENU, true);
    }

    public void setShowFamilyModeMenu(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_SHOW_FAMILY_MODE_MENU, enabled).apply();
    }

    public boolean getShowRecurringPaymentsMenu() {
        return prefs.getBoolean(PREFS_KEY_SHOW_RECURRING_PAYMENTS_MENU, true);
    }

    public void setShowRecurringPaymentsMenu(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_SHOW_RECURRING_PAYMENTS_MENU, enabled).apply();
    }

    public boolean getShowDigitalSignatureMenu() {
        return prefs.getBoolean(PREFS_KEY_SHOW_DIGITAL_SIGNATURE_MENU, true);
    }

    public void setShowDigitalSignatureMenu(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_SHOW_DIGITAL_SIGNATURE_MENU, enabled).apply();
    }

    public boolean getShowWriteCheckMenu() {
        return prefs.getBoolean(PREFS_KEY_SHOW_WRITE_CHECK_MENU, true);
    }

    public void setShowWriteCheckMenu(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_SHOW_WRITE_CHECK_MENU, enabled).apply();
    }

    public boolean getShowMultisigMenu() {
        return prefs.getBoolean(PREFS_KEY_SHOW_MULTISIG_MENU, true);
    }

    public void setShowMultisigMenu(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_SHOW_MULTISIG_MENU, enabled).apply();
    }

    public boolean getShowAuthenticationMenu() {
        return prefs.getBoolean(PREFS_KEY_SHOW_AUTHENTICATION_MENU, true);
    }

    public void setShowAuthenticationMenu(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_SHOW_AUTHENTICATION_MENU, enabled).apply();
    }

    public String getExchangeApiHost() {
        return prefs.getString(PREFS_KEY_EXCHANGE_API_HOST, "https://api.dogecoinwallet.org");
    }

    public void setExchangeApiHost(final String apiHost) {
        prefs.edit().putString(PREFS_KEY_EXCHANGE_API_HOST, apiHost).apply();
    }

    public String getExchangeEnvironment() {
        return prefs.getString(PREFS_KEY_EXCHANGE_ENVIRONMENT, "prod");
    }

    public void setExchangeEnvironment(final String environment) {
        prefs.edit().putString(PREFS_KEY_EXCHANGE_ENVIRONMENT, environment).apply();
    }

    public boolean getUseDogeEnabled() {
        return prefs.getBoolean(PREFS_KEY_USE_DOGE_ENABLED, true);
    }

    public void setUseDogeEnabled(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_USE_DOGE_ENABLED, enabled).apply();
    }

    public de.schildbach.wallet.ui.send.FeeCategory getDefaultFeeCategory() {
        final String categoryName = prefs.getString(PREFS_KEY_DEFAULT_FEE_CATEGORY, "ECONOMIC");
        try {
            return de.schildbach.wallet.ui.send.FeeCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid fee category in preferences: {}, defaulting to ECONOMIC", categoryName);
            return de.schildbach.wallet.ui.send.FeeCategory.ECONOMIC;
        }
    }

    public void setDefaultFeeCategory(final de.schildbach.wallet.ui.send.FeeCategory feeCategory) {
        prefs.edit().putString(PREFS_KEY_DEFAULT_FEE_CATEGORY, feeCategory.name()).apply();
    }

    public boolean getLabsExchangeEnabled() {
        return prefs.getBoolean(PREFS_KEY_LABS_EXCHANGE_ENABLED, false);
    }

    public void setLabsExchangeEnabled(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_LABS_EXCHANGE_ENABLED, enabled).apply();
    }

    public boolean getLabsAtomicSwapEnabled() {
        return prefs.getBoolean(PREFS_KEY_LABS_ATOMIC_SWAP_ENABLED, false);
    }

    public void setLabsAtomicSwapEnabled(final boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_LABS_ATOMIC_SWAP_ENABLED, enabled).apply();
    }

    // Legacy method for Atomic Swap (for compatibility with existing Atomic Swap code)
    public boolean getAtomicSwapEnabled() {
        return getLabsAtomicSwapEnabled();
    }

    public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
