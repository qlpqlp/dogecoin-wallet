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

import android.os.Build;
import android.text.format.DateUtils;
import com.google.common.io.BaseEncoding;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.MonetaryFormat;
import org.libdohj.params.AbstractDogecoinParams;
import org.libdohj.params.DogecoinMainNetParams;
import org.libdohj.params.DogecoinTestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public final class Constants {

    /** Network this wallet is on (e.g. testnet or mainnet). */
    public static final NetworkParameters NETWORK_PARAMETERS =
            DogecoinMainNetParams.get(); // Using mainnet for production

    /** Bitcoinj global context. */
    public static final Context CONTEXT = new Context(NETWORK_PARAMETERS);

    /**
     * The type of Bitcoin addresses used for the initial wallet: {@link Script.ScriptType#P2PKH} for classic
     * Base58, {@link Script.ScriptType#P2WPKH} for segwit Bech32.
     */
    public static final Script.ScriptType DEFAULT_OUTPUT_SCRIPT_TYPE = Script.ScriptType.P2PKH;

    /**
     * The type of Bitcoin addresses to upgrade the current wallet to: {@link Script.ScriptType#P2PKH} for classic
     * Base58, {@link Script.ScriptType#P2WPKH} for segwit Bech32.
     */
    public static final Script.ScriptType UPGRADE_OUTPUT_SCRIPT_TYPE = Script.ScriptType.P2PKH;

    /** Enable switch for synching of the block chain */
    public static final boolean ENABLE_BLOCKCHAIN_SYNC = true;
    /** Enable switch for fetching and showing of exchange rates */
    public static final boolean ENABLE_EXCHANGE_RATES = true;
    /** Enable switch for sweeping of paper wallets */
    public static final boolean ENABLE_SWEEP_WALLET = true;
    /** Enable switch for browsing to block explorers */
    public static final boolean ENABLE_BROWSE = true;

    public final static class Files {
        private static final String FILENAME_NETWORK_SUFFIX = NETWORK_PARAMETERS.getId()
                .equals(AbstractDogecoinParams.ID_DOGE_MAINNET) ? "" : "-testnet";

        /** Filename of the wallet. */
        public static final String WALLET_FILENAME_PROTOBUF = "wallet-protobuf" + FILENAME_NETWORK_SUFFIX;

        /** How often the wallet is autosaved. */
        public static final long WALLET_AUTOSAVE_DELAY_MS = 3 * DateUtils.SECOND_IN_MILLIS;

        /** Filename of the automatic key backup (old format, can only be read). */
        public static final String WALLET_KEY_BACKUP_BASE58 = "key-backup-base58" + FILENAME_NETWORK_SUFFIX;

        /** Filename of the automatic wallet backup. */
        public static final String WALLET_KEY_BACKUP_PROTOBUF = "key-backup-protobuf" + FILENAME_NETWORK_SUFFIX;

        /** Filename of the manual wallet backup. */
        public static final String EXTERNAL_WALLET_BACKUP = "dogecoin-wallet-backup" + FILENAME_NETWORK_SUFFIX;

        /** Filename of the block store for storing the chain. */
        public static final String BLOCKCHAIN_FILENAME = "blockchain" + FILENAME_NETWORK_SUFFIX;

        /** Capacity of the block store. */
        public static final int BLOCKCHAIN_STORE_CAPACITY = 10000;

        /** Name of the asset containing the block checkpoints. */
        public static final String CHECKPOINTS_ASSET = "checkpoints.txt";

        /** Name of the asset containing hardcoded fees. */
        public static final String FEES_ASSET = "fees.txt";

        /** Filename of the dynamic fees file. */
        public static final String FEES_FILENAME = "fees" + FILENAME_NETWORK_SUFFIX + ".txt";

        /** Name of the asset containing Electrum servers. */
        public static final String ELECTRUM_SERVERS_ASSET = "electrum-servers.txt";
    }

    /** URL to fetch version alerts from. */
    public static final HttpUrl VERSION_URL = HttpUrl.parse("https://maxkeller.io/version"
            + (NETWORK_PARAMETERS.getId().equals(NetworkParameters.ID_MAINNET) ? "" : "-test"));
    /** URL to fetch dynamic fees from. */
    public static final HttpUrl DYNAMIC_FEES_URL = HttpUrl.parse("https://wallet.schildbach.de/fees");

    /** MIME type used for transmitting single transactions. */
    public static final String MIMETYPE_TRANSACTION = "application/x-dogetx";

    /** MIME type used for transmitting wallet backups. */
    public static final String MIMETYPE_WALLET_BACKUP = "application/x-dogecoin-wallet-backup";

    /** Number of confirmations until a transaction is fully confirmed. */
    public static final int MAX_NUM_CONFIRMATIONS = 7;

    /** User-agent to use for network access. */
    public static final String USER_AGENT = "Dogecoin Wallet";

    /** Default currency to use if all default mechanisms fail. */
    public static final String DEFAULT_EXCHANGE_CURRENCY = "USD";

    /** Donation address for tip/donate action. */
    public static final String DONATION_ADDRESS = null;

    /** Recipient e-mail address for reports. */
    public static final String REPORT_EMAIL = "report@dogecoinwallet.org";

    /** Subject line for manually reported issues. */
    public static final String REPORT_SUBJECT_ISSUE = "Reported issue";

    /** Subject line for crash reports. */
    public static final String REPORT_SUBJECT_CRASH = "Crash report";

    public static final char CHAR_HAIR_SPACE = '\u200a';
    public static final char CHAR_THIN_SPACE = '\u2009';
    public static final char CHAR_BITCOIN = '\u00d0';
    public static final char CHAR_ALMOST_EQUAL_TO = '\u2248';
    public static final char CHAR_CHECKMARK = '\u2713';
    public static final char CHAR_CROSSMARK = '\u2715';
    public static final char CURRENCY_PLUS_SIGN = '\uff0b';
    public static final char CURRENCY_MINUS_SIGN = '\uff0d';
    public static final String PREFIX_ALMOST_EQUAL_TO = Character.toString(CHAR_ALMOST_EQUAL_TO) + CHAR_THIN_SPACE;
    public static final int ADDRESS_FORMAT_GROUP_SIZE = 4;
    public static final int ADDRESS_FORMAT_LINE_SIZE = 12;

    public static final MonetaryFormat LOCAL_FORMAT = new MonetaryFormat().noCode().minDecimals(4).optionalDecimals();

    public static final BaseEncoding HEX = BaseEncoding.base16().lowerCase();

    public static final String BLOCKCYPHER_API_URL = "https://api.blockcypher.com/v1/doge/main/addrs/";

    public static final String SOURCE_URL = "https://github.com/qlpqlp/dogecoin-wallet";
    public static final String BINARY_URL = "https://github.com/qlpqlp/dogecoin-wallet/releases/latest";

    public static final int PEER_DISCOVERY_TIMEOUT_MS = 5 * (int) DateUtils.SECOND_IN_MILLIS;
    public static final int PEER_TIMEOUT_MS = 15 * (int) DateUtils.SECOND_IN_MILLIS;
    
    /** Dogecoin DNS seed servers for worldwide peer discovery */
    public static final String[] DOGECOIN_DNS_SEEDS = {
        "seed.multidoge.org",
        "seed2.multidoge.org"
    };
    
    /** Interval for periodic worldwide peer discovery (in milliseconds) */
    public static final long WORLDWIDE_PEER_DISCOVERY_INTERVAL_MS = 15 * DateUtils.MINUTE_IN_MILLIS; // Increased from 5 to 15 minutes to reduce memory pressure
    public static final long DISCOVERY_TIMEOUT_MS = 2 * DateUtils.MINUTE_IN_MILLIS; // 2 minute timeout for better discovery
    public static final int MAX_DISCOVERY_ATTEMPTS = 15; // Try more times for better results
    
    /** Enhanced DNS discovery settings */
    public static final int DNS_QUERY_RETRIES = 5; // Number of times to query each DNS seed
    public static final int DNS_QUERY_DELAY_MS = 100; // Delay between DNS queries (ms)
    public static final int DNS_ROTATION_DELAY_MIN_MS = 10000; // Minimum delay to catch DNS rotation (10 seconds)
    public static final int DNS_ROTATION_DELAY_MAX_MS = 20000; // Maximum delay to catch DNS rotation (20 seconds)
    public static final int MAX_PEERS_PER_DNS_QUERY = 50; // Maximum peers to get per DNS query
    public static final int HANDSHAKE_TIMEOUT_MS = 5 * (int) DateUtils.SECOND_IN_MILLIS; // Handshake timeout
    
    /** Snowball peer discovery settings */
    public static final int MAX_SNOWBALL_ROUNDS = 3; // Number of snowball discovery rounds
    public static final int MAX_PEERS_PER_SNOWBALL_ROUND = 50; // Max peers to query per round
    public static final int PEER_QUERY_TIMEOUT_MS = 10 * (int) DateUtils.SECOND_IN_MILLIS; // Timeout for peer queries
    public static final int SNOWBALL_DELAY_MS = 2000; // Delay between snowball rounds (2 seconds)

    public static final long LAST_USAGE_THRESHOLD_JUST_MS = DateUtils.HOUR_IN_MILLIS;
    public static final long LAST_USAGE_THRESHOLD_TODAY_MS = DateUtils.DAY_IN_MILLIS;
    public static final long LAST_USAGE_THRESHOLD_RECENTLY_MS = DateUtils.WEEK_IN_MILLIS;
    public static final long LAST_USAGE_THRESHOLD_INACTIVE_MS = 4 * DateUtils.WEEK_IN_MILLIS;

    public static final long DELAYED_TRANSACTION_THRESHOLD_MS = 2 * DateUtils.HOUR_IN_MILLIS;

    public static final long AUTOCLOSE_DELAY_MS = 1000;

    /** A balance above this amount will show a warning */
    public static final Coin TOO_MUCH_BALANCE_THRESHOLD = Coin.COIN.multiply(10000);
    /** A balance above this amount will cause the donate option to be shown */
    public static final Coin SOME_BALANCE_THRESHOLD = Coin.COIN.multiply(100);

    public static final int SDK_DEPRECATED_BELOW = Build.VERSION_CODES.M;
    public static final String SECURITY_PATCH_INSECURE_BELOW = "2018-01-01";

    public static final int NOTIFICATION_ID_CONNECTIVITY = 1;
    public static final int NOTIFICATION_ID_COINS_RECEIVED = 2;
    public static final int NOTIFICATION_ID_BLUETOOTH = 3;
    public static final int NOTIFICATION_ID_INACTIVITY = 4;
    public static final String NOTIFICATION_GROUP_KEY_RECEIVED = "group-received";
    public static final String NOTIFICATION_CHANNEL_ID_RECEIVED = "received";
    public static final String NOTIFICATION_CHANNEL_ID_ONGOING = "ongoing";
    public static final String NOTIFICATION_CHANNEL_ID_IMPORTANT = "important";

    /** Desired number of scrypt iterations for deriving the spending PIN */
    public static final int SCRYPT_ITERATIONS_TARGET = 65536;
    public static final int SCRYPT_ITERATIONS_TARGET_LOWRAM = 32768;

    /** Default ports for Electrum servers */
    public static final int ELECTRUM_SERVER_DEFAULT_PORT_TCP = NETWORK_PARAMETERS.getId()
            .equals(NetworkParameters.ID_MAINNET) ? 50001 : 51001;
    public static final int ELECTRUM_SERVER_DEFAULT_PORT_TLS = NETWORK_PARAMETERS.getId()
            .equals(NetworkParameters.ID_MAINNET) ? 50002 : 51002;

    /** Shared HTTP client, can reuse connections */
    public static final OkHttpClient HTTP_CLIENT;
    static {
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(
                new HttpLoggingInterceptor.Logger() {
                    @Override
                    public void log(final String message) {
                        log.debug(message);
                    }
                });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        final OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        httpClientBuilder.followRedirects(false);
        httpClientBuilder.followSslRedirects(true);
        httpClientBuilder.connectTimeout(15, TimeUnit.SECONDS);
        httpClientBuilder.writeTimeout(15, TimeUnit.SECONDS);
        httpClientBuilder.readTimeout(15, TimeUnit.SECONDS);
        httpClientBuilder.addInterceptor(loggingInterceptor);
        HTTP_CLIENT = httpClientBuilder.build();
    }

    private static final Logger log = LoggerFactory.getLogger(Constants.class);
}
