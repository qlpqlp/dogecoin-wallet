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

package de.schildbach.wallet.exchangerate;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okio.BufferedSource;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Andreas Schildbach
 */
public final class CoinGecko {
    private static final HttpUrl URL = HttpUrl.parse("https://api.coingecko.com/api/v3/exchange_rates");
    private static final String DIRECT_URL_BASE = "https://api.coingecko.com/api/v3/simple/price?ids=dogecoin&vs_currencies=";
    private static final MediaType MEDIA_TYPE = MediaType.get("application/json");
    private static final String SOURCE = "CoinGecko.com";

    private static final Logger log = LoggerFactory.getLogger(CoinGecko.class);

    private final Moshi moshi;

    public CoinGecko(final Moshi moshi) {
        this.moshi = moshi;
    }

    public MediaType mediaType() {
        return MEDIA_TYPE;
    }

    public HttpUrl url() {
        return URL;
    }

    public HttpUrl directUrl(final String currencyCode) {
        return HttpUrl.parse(DIRECT_URL_BASE + currencyCode.toLowerCase());
    }

    public HttpUrl directUrlMultiple(final String[] currencyCodes) {
        final StringBuilder urlBuilder = new StringBuilder(DIRECT_URL_BASE);
        for (int i = 0; i < currencyCodes.length; i++) {
            if (i > 0) urlBuilder.append(",");
            urlBuilder.append(currencyCodes[i].toLowerCase());
        }
        return HttpUrl.parse(urlBuilder.toString());
    }

    public List<ExchangeRateEntry> parse(final BufferedSource jsonSource, double conv) throws IOException {
        final JsonAdapter<Response> jsonAdapter = moshi.adapter(Response.class);
        final Response jsonResponse = jsonAdapter.fromJson(jsonSource);
        final List<ExchangeRateEntry> result = new ArrayList<>(jsonResponse.rates.size());
        for (Map.Entry<String, ExchangeRateJson> entry : jsonResponse.rates.entrySet()) {
            final String symbol = entry.getKey().toUpperCase(Locale.US);
            final ExchangeRateJson exchangeRate = entry.getValue();
            if (exchangeRate.type == Type.FIAT) {
                try {
                    final String rate = exchangeRate.value;
                    final double btcRate = Double.parseDouble(Fiat.parseFiatInexact(symbol, rate).toPlainString());
                    DecimalFormat df = new DecimalFormat("#.########");
                    df.setRoundingMode(RoundingMode.HALF_UP);
                    DecimalFormatSymbols dfs = new DecimalFormatSymbols();
                    dfs.setDecimalSeparator('.');
                    dfs.setGroupingSeparator(',');
                    df.setDecimalFormatSymbols(dfs);
                    final Fiat dogeRate = Fiat.parseFiatInexact(symbol, df.format(btcRate*conv));

                    if (dogeRate.signum() > 0)
                        result.add(new ExchangeRateEntry(SOURCE, new ExchangeRate(dogeRate)));
                } catch (final ArithmeticException x) {
                    log.warn("problem parsing {} exchange rate from {}: {}", symbol, URL, x.getMessage());
                }
            }
        }
        return result;
    }

    public List<ExchangeRateEntry> parseDirect(final BufferedSource jsonSource, final String currencyCode) throws IOException {
        final JsonAdapter<DirectResponse> jsonAdapter = moshi.adapter(DirectResponse.class);
        final DirectResponse jsonResponse = jsonAdapter.fromJson(jsonSource);
        final List<ExchangeRateEntry> result = new ArrayList<>();
        
        if (jsonResponse.dogecoin != null) {
            for (Map.Entry<String, Double> entry : jsonResponse.dogecoin.entrySet()) {
                final String symbol = entry.getKey().toUpperCase(Locale.US);
                final Double rate = entry.getValue();
                
                if (rate != null && rate > 0) {
                    try {
                        // Create a Fiat object with 1 DOGE = rate amount of currency
                        final Fiat dogeRate = Fiat.parseFiatInexact(symbol, rate.toString());
                        if (dogeRate.signum() > 0) {
                            // Create exchange rate: 1 DOGE = dogeRate amount of currency
                            result.add(new ExchangeRateEntry(SOURCE, new ExchangeRate(dogeRate)));
                        }
                    } catch (final ArithmeticException x) {
                        log.warn("problem parsing {} exchange rate from direct API: {}", symbol, x.getMessage());
                    }
                }
            }
        }
        
        return result;
    }

    public List<ExchangeRateEntry> parseDirectMultiple(final BufferedSource jsonSource) throws IOException {
        final JsonAdapter<DirectResponse> jsonAdapter = moshi.adapter(DirectResponse.class);
        final DirectResponse jsonResponse = jsonAdapter.fromJson(jsonSource);
        final List<ExchangeRateEntry> result = new ArrayList<>();
        
        if (jsonResponse.dogecoin != null) {
            for (Map.Entry<String, Double> entry : jsonResponse.dogecoin.entrySet()) {
                final String symbol = entry.getKey().toUpperCase(Locale.US);
                final Double rate = entry.getValue();
                
                if (rate != null && rate > 0) {
                    try {
                        // Create a Fiat object with 1 DOGE = rate amount of currency
                        final Fiat dogeRate = Fiat.parseFiatInexact(symbol, rate.toString());
                        if (dogeRate.signum() > 0) {
                            // Create exchange rate: 1 DOGE = dogeRate amount of currency
                            result.add(new ExchangeRateEntry(SOURCE, new ExchangeRate(dogeRate)));
                        }
                    } catch (final ArithmeticException x) {
                        log.warn("problem parsing {} exchange rate from direct API: {}", symbol, x.getMessage());
                    }
                }
            }
        }
        
        return result;
    }

    private enum Type {
        @Json(name = "crypto")
        CRYPTO,
        @Json(name = "fiat")
        FIAT,
        @Json(name = "commodity")
        COMMODITY
    }

    private static class Response {
        public Map<String, ExchangeRateJson> rates;
    }

    private static class ExchangeRateJson {
        public String name;
        public String unit;
        public String value;
        public Type type;
    }

    private static class DirectResponse {
        public Map<String, Double> dogecoin;
    }
}
