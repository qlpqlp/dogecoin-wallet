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

package de.schildbach.wallet.ui.send;

import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.hash.Hashing;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.util.Assets;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Andreas Schildbach
 */
public final class RequestWalletBalanceTask {
    private final Handler backgroundHandler;
    private final Handler callbackHandler;
    private final ResultCallback resultCallback;

    private static final Logger log = LoggerFactory.getLogger(RequestWalletBalanceTask.class);

    public interface ResultCallback {
        void onResult(Set<UTXO> utxos);

        void onFail(int messageResId, Object... messageArgs);
    }

    public RequestWalletBalanceTask(final Handler backgroundHandler, final ResultCallback resultCallback) {
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
        this.resultCallback = resultCallback;
    }

    public void requestWalletBalance(final AssetManager assets, final ECKey key) {
        requestWalletBalance(assets, key, null);
    }

    public void requestWalletBalance(final AssetManager assets, final ECKey key, @Nullable final String p2shAddress) {
        backgroundHandler.post(() -> {
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);

            final Address legacyAddress = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, key);
            
            // If P2SH address is provided (for CLTV checks), query both addresses separately
            // BlockCypher API doesn't support comma-separated addresses in URL path
            if (p2shAddress != null && !p2shAddress.trim().isEmpty()) {
                log.info("Querying both P2PKH ({}) and P2SH ({}) addresses", legacyAddress.toString(), p2shAddress);
                
                // Query both addresses and combine results
                final Set<UTXO> combinedUtxos = new HashSet<>();
                final java.util.concurrent.atomic.AtomicInteger pendingRequests = new java.util.concurrent.atomic.AtomicInteger(2);
                final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);
                
                // Query P2PKH address
                queryAddress(legacyAddress.toString(), new java.util.function.Consumer<Set<UTXO>>() {
                    @Override
                    public void accept(Set<UTXO> utxos) {
                        synchronized (combinedUtxos) {
                            combinedUtxos.addAll(utxos);
                            if (pendingRequests.decrementAndGet() == 0 && !hasError.get()) {
                                log.info("Fetched unspent outputs from both addresses: {} UTXOs total", combinedUtxos.size());
                                onResult(combinedUtxos);
                            }
                        }
                    }
                }, new ResultCallback() {
                    @Override
                    public void onResult(Set<UTXO> utxos) {
                        // Should not be called
                    }
                    
                    @Override
                    public void onFail(int messageResId, Object... messageArgs) {
                        if (!hasError.getAndSet(true)) {
                            RequestWalletBalanceTask.this.onFail(messageResId, messageArgs);
                        }
                    }
                });
                
                // Query P2SH address
                queryAddress(p2shAddress.trim(), new java.util.function.Consumer<Set<UTXO>>() {
                    @Override
                    public void accept(Set<UTXO> utxos) {
                        synchronized (combinedUtxos) {
                            combinedUtxos.addAll(utxos);
                            if (pendingRequests.decrementAndGet() == 0 && !hasError.get()) {
                                log.info("Fetched unspent outputs from both addresses: {} UTXOs total", combinedUtxos.size());
                                onResult(combinedUtxos);
                            }
                        }
                    }
                }, new ResultCallback() {
                    @Override
                    public void onResult(Set<UTXO> utxos) {
                        // Should not be called
                    }
                    
                    @Override
                    public void onFail(int messageResId, Object... messageArgs) {
                        if (!hasError.getAndSet(true)) {
                            RequestWalletBalanceTask.this.onFail(messageResId, messageArgs);
                        }
                    }
                });
            } else {
                // Single address query (legacy behavior)
                queryAddress(legacyAddress.toString(), new java.util.function.Consumer<Set<UTXO>>() {
                    @Override
                    public void accept(Set<UTXO> utxos) {
                        onResult(utxos);
                    }
                }, new ResultCallback() {
                    @Override
                    public void onResult(Set<UTXO> utxos) {
                        // Should not be called
                    }
                    
                    @Override
                    public void onFail(int messageResId, Object... messageArgs) {
                        RequestWalletBalanceTask.this.onFail(messageResId, messageArgs);
                    }
                });
            }
        });
    }
    
    private void queryAddress(final String address, final java.util.function.Consumer<Set<UTXO>> onSuccess, final ResultCallback onFail) {
        // Use either dogechain or chain.so
        List<String> urls = new ArrayList<>(2);
        urls.add(Constants.BLOCKCYPHER_API_URL);
        //urls.add(Constants.DOGECHAIN_API_URL); // Seems unreliable too now
        //urls.add(Constants.CHAINSO_API_URL); // inactive for now
        Collections.shuffle(urls, new Random(System.nanoTime()));

        final StringBuilder url = new StringBuilder(urls.get(0));
        url.append(address);

        log.debug("trying to request wallet balance from {}", url);

        final Request.Builder request = new Request.Builder();
        request.url(HttpUrl.parse(url.toString()).newBuilder().encodedQuery("unspentOnly=true&includeScript=true").build());

        final Call call = Constants.HTTP_CLIENT.newCall(request.build());

        try {
            final Response response = call.execute();
            if (response.isSuccessful()) {
                String content = response.body().string();
                final JSONObject json = new JSONObject(content);
                final JSONArray jsonOutputs = json.optJSONArray("txrefs");

                final Set<UTXO> utxoSet = new HashSet<>();
                if (jsonOutputs != null) {
                    for (int i = 0; i < jsonOutputs.length(); i++) {
                        final JSONObject jsonOutput = jsonOutputs.getJSONObject(i);

                        final Sha256Hash utxoHash = Sha256Hash.wrap(jsonOutput.getString("tx_hash"));
                        final int utxoIndex = jsonOutput.getInt("tx_output_n");
                        final byte[] utxoScriptBytes = Hex.decode(jsonOutput.getString("script"));
                        final Coin uxtutx = Coin.valueOf(Long.parseLong(jsonOutput.getString("value")));

                        UTXO utxo = new UTXO(utxoHash, utxoIndex, uxtutx, -1, false, new Script(utxoScriptBytes));
                        utxoSet.add(utxo);
                    }
                }

                log.info("fetched {} unspent outputs from {}", utxoSet.size(), url);
                onSuccess.accept(utxoSet);
            } else {
                final String responseMessage = response.message();
                log.info("got http error '{}: {}' from {}", response.code(), responseMessage, url);
                onFail.onFail(R.string.error_http, response.code(), responseMessage);
            }
        } catch (final JSONException x) {
            log.info("problem parsing json from " + url, x);
            onFail.onFail(R.string.error_parse, x.getMessage());
        } catch (final IOException x) {
            log.info("problem querying unspent outputs from " + url, x);
            onFail.onFail(R.string.error_io, x.getMessage());
        }
    }

    protected void onResult(final Set<UTXO> utxos) {
        callbackHandler.post(() -> resultCallback.onResult(utxos));
    }

    protected void onFail(final int messageResId, final Object... messageArgs) {
        callbackHandler.post(() -> resultCallback.onFail(messageResId, messageArgs));
    }
}
