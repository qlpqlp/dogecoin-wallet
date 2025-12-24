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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.AtomicSwap;
import de.schildbach.wallet.data.AtomicSwapDao;
import de.schildbach.wallet.service.AtomicSwapService;
import de.schildbach.wallet.ui.AbstractWalletActivityViewModel;
import de.schildbach.wallet.util.HtlcUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Atomic Swap Activity
 * 
 * Allows users to initiate and manage cross-chain atomic swaps (DOGE ↔ BTC, DOGE ↔ LTC)
 * using Hash Time Lock Contracts (HTLC).
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class AtomicSwapActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(AtomicSwapActivity.class);
    
    private WalletApplication application;
    private Configuration config;
    private AddressBookDatabase database;
    private AtomicSwapDao swapDao;
    private AbstractWalletActivityViewModel walletActivityViewModel;
    
    private RecyclerView recyclerSwaps;
    private LinearLayout layoutEmptyState;
    private Button btnInitiateSwap;
    private Button btnInitiateSwapBottom;
    private Button btnRefresh;
    private AtomicSwapsAdapter adapter;
    private List<AtomicSwap> swaps = new ArrayList<>();
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        application = getWalletApplication();
        config = application.getConfiguration();
        database = AddressBookDatabase.getDatabase(this);
        swapDao = database.atomicSwapDao();
        walletActivityViewModel = new AbstractWalletActivityViewModel(application);
        
        // Check if atomic swap is enabled in Labs
        if (!config.getLabsAtomicSwapEnabled()) {
            Toast.makeText(this, "Atomic Swap is disabled. Please enable it in Settings > Configuration > Labs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        setContentView(R.layout.activity_atomic_swap);
        
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(R.string.atomic_swap_activity_title);
        
        // Initialize views
        recyclerSwaps = findViewById(R.id.recycler_swaps);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        btnInitiateSwap = findViewById(R.id.btn_initiate_swap);
        btnInitiateSwapBottom = findViewById(R.id.btn_initiate_swap_bottom);
        btnRefresh = findViewById(R.id.btn_refresh);
        
        // Setup RecyclerView
        recyclerSwaps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AtomicSwapsAdapter();
        recyclerSwaps.setAdapter(adapter);
        
        // Setup button listeners
        btnInitiateSwap.setOnClickListener(v -> showInitiateSwapDialog());
        btnInitiateSwapBottom.setOnClickListener(v -> showInitiateSwapDialog());
        btnRefresh.setOnClickListener(v -> loadSwaps());
        
        // Set button text colors based on theme (golden in light mode, amber in dark mode)
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int buttonTextColor = isDarkMode ? 
            ContextCompat.getColor(this, R.color.amber) : 
            ContextCompat.getColor(this, R.color.colorPrimary);
        btnInitiateSwap.setTextColor(buttonTextColor);
        btnInitiateSwapBottom.setTextColor(buttonTextColor);
        
        // Start monitoring service
        startService(new Intent(this, AtomicSwapService.class));
        
        // Load swaps
        loadSwaps();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload swaps when returning to this activity
        loadSwaps();
    }
    
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void loadSwaps() {
        try {
            swaps = swapDao.getAllSwaps();
            adapter.notifyDataSetChanged();
            
            if (swaps.isEmpty()) {
                layoutEmptyState.setVisibility(View.VISIBLE);
                recyclerSwaps.setVisibility(View.GONE);
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                recyclerSwaps.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            log.error("Error loading swaps", e);
            Toast.makeText(this, "Error loading swaps: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showInitiateSwapDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.My_Theme_Dialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_initiate_atomic_swap, null);
        builder.setView(dialogView);
        
        Spinner spinnerFromCurrency = dialogView.findViewById(R.id.spinner_from_currency);
        Spinner spinnerToCurrency = dialogView.findViewById(R.id.spinner_to_currency);
        EditText editFromAmount = dialogView.findViewById(R.id.edit_from_amount);
        EditText editToAmount = dialogView.findViewById(R.id.edit_to_amount);
        EditText editCounterpartyAddress = dialogView.findViewById(R.id.edit_counterparty_address);
        TextView textMyReceivingAddress = dialogView.findViewById(R.id.text_my_receiving_address);
        EditText editMyReceivingAddress = dialogView.findViewById(R.id.edit_my_receiving_address);
        Button btnCreate = dialogView.findViewById(R.id.btn_create_swap);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        // Set button text colors based on theme (golden in light mode, amber in dark mode)
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int buttonTextColor = isDarkMode ? 
            ContextCompat.getColor(this, R.color.amber) : 
            ContextCompat.getColor(this, R.color.colorPrimary);
        btnCreate.setTextColor(buttonTextColor);
        btnCancel.setTextColor(buttonTextColor);
        
        // Setup currency spinners with custom adapter for proper text colors
        // From currency: Only DOGE (we can only create/broadcast DOGE transactions)
        String[] fromCurrencies = {"DOGE"};
        ArrayAdapter<String> fromCurrencyAdapter = new ArrayAdapter<String>(this, 
            android.R.layout.simple_spinner_item, fromCurrencies) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                if (textView != null) {
                    textView.setTextColor(ContextCompat.getColor(AtomicSwapActivity.this, R.color.fg_significant));
                }
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                // Use the custom layout which already has proper colors set
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                if (textView != null) {
                    // Ensure text color is set (layout should handle background)
                    textView.setTextColor(ContextCompat.getColor(AtomicSwapActivity.this, R.color.fg_significant));
                }
                return view;
            }
        };
        fromCurrencyAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerFromCurrency.setAdapter(fromCurrencyAdapter);
        spinnerFromCurrency.setEnabled(false); // Disable since only one option
        
        // To currency: BTC, LTC (counterparty will create those contracts)
        String[] toCurrencies = {"BTC", "LTC"};
        ArrayAdapter<String> toCurrencyAdapter = new ArrayAdapter<String>(this, 
            android.R.layout.simple_spinner_item, toCurrencies) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                if (textView != null) {
                    textView.setTextColor(ContextCompat.getColor(AtomicSwapActivity.this, R.color.fg_significant));
                }
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                // Use the custom layout which already has proper colors set
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                if (textView != null) {
                    // Ensure text color is set (layout should handle background)
                    textView.setTextColor(ContextCompat.getColor(AtomicSwapActivity.this, R.color.fg_significant));
                }
                return view;
            }
        };
        toCurrencyAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerToCurrency.setAdapter(toCurrencyAdapter);
        
        // Set default: DOGE -> BTC
        spinnerFromCurrency.setSelection(0); // DOGE
        spinnerToCurrency.setSelection(0); // BTC
        
        // Show/hide "Your Receiving Address" field based on "To" currency
        spinnerToCurrency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String toCurrency = parent.getItemAtPosition(position).toString();
                if ("BTC".equals(toCurrency) || "LTC".equals(toCurrency)) {
                    textMyReceivingAddress.setVisibility(View.VISIBLE);
                    editMyReceivingAddress.setVisibility(View.VISIBLE);
                    editMyReceivingAddress.setHint("Enter your " + toCurrency + " address (where you receive " + toCurrency + ")");
                } else {
                    textMyReceivingAddress.setVisibility(View.GONE);
                    editMyReceivingAddress.setVisibility(View.GONE);
                }
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                textMyReceivingAddress.setVisibility(View.GONE);
                editMyReceivingAddress.setVisibility(View.GONE);
            }
        });
        
        // Initially show/hide based on default selection
        textMyReceivingAddress.setVisibility(View.VISIBLE);
        editMyReceivingAddress.setVisibility(View.VISIBLE);
        editMyReceivingAddress.setHint("Enter your BTC address (where you receive BTC)");
        
        AlertDialog dialog = builder.create();
        
        // Apply proper dialog styling with square corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background_square);
        }
        
        // Customize dialog title - make it amber and center vertically
        dialog.setOnShowListener(dialogInterface -> {
            TextView titleView = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
            if (titleView == null) {
                // Try alternative title view ID
                titleView = dialog.findViewById(android.R.id.title);
            }
            if (titleView != null) {
                titleView.setTextColor(ContextCompat.getColor(AtomicSwapActivity.this, R.color.amber));
                titleView.setGravity(android.view.Gravity.CENTER_VERTICAL);
            }
        });
        
        dialog.setTitle(R.string.atomic_swap_initiate);
        
        btnCreate.setOnClickListener(v -> {
            String fromCurrency = spinnerFromCurrency.getSelectedItem().toString();
            String toCurrency = spinnerToCurrency.getSelectedItem().toString();
            String fromAmountStr = editFromAmount.getText().toString().trim();
            String toAmountStr = editToAmount.getText().toString().trim();
            String counterpartyAddress = editCounterpartyAddress.getText().toString().trim();
            String myReceivingAddress = editMyReceivingAddress.getText().toString().trim();
            
            if (TextUtils.isEmpty(fromAmountStr) || TextUtils.isEmpty(toAmountStr) || 
                TextUtils.isEmpty(counterpartyAddress)) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate "Your Receiving Address" for BTC/LTC swaps
            if (("BTC".equals(toCurrency) || "LTC".equals(toCurrency)) && TextUtils.isEmpty(myReceivingAddress)) {
                Toast.makeText(this, "Please enter your " + toCurrency + " address where you want to receive " + toCurrency, Toast.LENGTH_LONG).show();
                return;
            }
            
            if (fromCurrency.equals(toCurrency)) {
                Toast.makeText(this, "From and To currencies must be different", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate counterparty Dogecoin address
            try {
                org.bitcoinj.core.Address.fromString(Constants.NETWORK_PARAMETERS, counterpartyAddress);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid Dogecoin address for counterparty. Please enter a valid Dogecoin address where the counterparty will receive DOGE.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Validate receiving address (Bitcoin/Litecoin) if provided
            if (("BTC".equals(toCurrency) || "LTC".equals(toCurrency)) && !TextUtils.isEmpty(myReceivingAddress)) {
                try {
                    org.bitcoinj.core.NetworkParameters params = de.schildbach.wallet.util.MultiChainNetworkHelper.getNetworkParameters(toCurrency);
                    org.bitcoinj.core.Address.fromString(params, myReceivingAddress);
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid " + toCurrency + " address. Please enter a valid " + toCurrency + " address where you will receive " + toCurrency + ".", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            
            try {
                // Convert amounts to smallest unit (satoshis/litoshis/smallest DOGE unit)
                long fromAmount = parseAmount(fromAmountStr, fromCurrency);
                long toAmount = parseAmount(toAmountStr, toCurrency);
                
                // Validate that From currency is DOGE (only currency we can send)
                if (!"DOGE".equals(fromCurrency)) {
                    Toast.makeText(this, "Only DOGE swaps can be initiated. BTC/LTC swaps require external wallet integration.", Toast.LENGTH_LONG).show();
                    return;
                }
                
                // Get wallet asynchronously (it may not be loaded yet)
                application.getWalletAsync(wallet -> {
                    if (wallet == null) {
                        runOnUiThread(() -> {
                            Toast.makeText(AtomicSwapActivity.this, "Wallet not available. Please wait for wallet to load.", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                    
                    // Check if wallet has sufficient balance
                    Coin availableBalance = wallet.getBalance();
                    Coin swapAmount = Coin.valueOf(fromAmount);
                    
                    // Add estimated fee (1 DOGE per KB, estimate ~250 bytes for contract transaction)
                    Coin estimatedFee = Coin.valueOf(250000); // ~0.25 DOGE
                    Coin totalRequired = swapAmount.add(estimatedFee);
                    
                    if (availableBalance.isLessThan(totalRequired)) {
                        Coin missing = totalRequired.subtract(availableBalance);
                        String balanceStr = availableBalance.toPlainString();
                        String requiredStr = totalRequired.toPlainString();
                        String missingStr = missing.toPlainString();
                        runOnUiThread(() -> {
                            Toast.makeText(AtomicSwapActivity.this, 
                                String.format("Insufficient balance!\nAvailable: %s DOGE\nRequired: %s DOGE\nMissing: %s DOGE", 
                                    balanceStr, requiredStr, missingStr), 
                                Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                    
                    // Create swap
                    AtomicSwap newSwap = AtomicSwapService.createSwap(AtomicSwapActivity.this, fromCurrency, toCurrency, 
                                                                   fromAmount, toAmount, counterpartyAddress, myReceivingAddress);
                    
                    runOnUiThread(() -> {
                        Toast.makeText(AtomicSwapActivity.this, "Atomic swap created successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        loadSwaps();
                        
                        // Show swap details dialog with secret hash to share with counterparty
                        showSwapDetails(newSwap);
                    });
                    
                    // Create and broadcast HTLC contract (only DOGE supported)
                    runOnUiThread(() -> {
                        Toast.makeText(AtomicSwapActivity.this, "Creating HTLC contract...", Toast.LENGTH_SHORT).show();
                    });
                    AtomicSwapService.createHtlcContract(AtomicSwapActivity.this, newSwap.getId(), wallet)
                        .thenAccept(txId -> {
                            runOnUiThread(() -> {
                                Toast.makeText(AtomicSwapActivity.this, "HTLC contract created: " + txId, Toast.LENGTH_LONG).show();
                                loadSwaps();
                            });
                        })
                        .exceptionally(e -> {
                            runOnUiThread(() -> {
                                Toast.makeText(AtomicSwapActivity.this, "Error creating contract: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                log.error("Error creating HTLC contract", e);
                            });
                            return null;
                        });
                });
            } catch (Exception e) {
                log.error("Error creating swap", e);
                Toast.makeText(this, "Error creating swap: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private long parseAmount(String amountStr, String currency) {
        try {
            double amount = Double.parseDouble(amountStr);
            // Convert to smallest unit based on currency
            if ("DOGE".equals(currency)) {
                return (long) (amount * Coin.COIN.value);
            } else if ("BTC".equals(currency)) {
                return (long) (amount * 100000000); // 1 BTC = 100,000,000 satoshis
            } else if ("LTC".equals(currency)) {
                return (long) (amount * 100000000); // 1 LTC = 100,000,000 litoshis
            }
            return 0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format");
        }
    }
    
    private void showSwapDetails(AtomicSwap swap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.My_Theme_Dialog);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_swap_details, null);
        builder.setView(dialogView);
        builder.setTitle("Swap Details");
        
        TextView textSecretHash = dialogView.findViewById(R.id.text_secret_hash);
        TextView textSwapInfo = dialogView.findViewById(R.id.text_swap_info);
        TextView textDogeSendingAddress = dialogView.findViewById(R.id.text_doge_sending_address);
        TextView textReceivingAddressLabel = dialogView.findViewById(R.id.text_receiving_address_label);
        TextView textReceivingAddress = dialogView.findViewById(R.id.text_receiving_address);
        TextView textTxIdLabel = dialogView.findViewById(R.id.text_tx_id_label);
        TextView textTxId = dialogView.findViewById(R.id.text_tx_id);
        LinearLayout layoutTxId = dialogView.findViewById(R.id.layout_tx_id);
        ImageButton btnCopyHash = dialogView.findViewById(R.id.btn_copy_hash);
        ImageButton btnCopyTxId = dialogView.findViewById(R.id.btn_copy_tx_id);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        // Set swap info
        textSwapInfo.setText(String.format("Swap ID: %d\nFrom: %s %s\nTo: %s %s\n\nShare the secret hash with your counterparty to complete the swap.",
            swap.getId(), formatAmount(swap.getFromAmount(), swap.getFromCurrency()), swap.getFromCurrency(),
            formatAmount(swap.getToAmount(), swap.getToCurrency()), swap.getToCurrency()));
        
        // Set secret hash
        textSecretHash.setText(swap.getSecretHash());
        
        // Set DOGE sending address (counterparty address)
        if (swap.getCounterpartyAddress() != null && !swap.getCounterpartyAddress().isEmpty()) {
            textDogeSendingAddress.setText(swap.getCounterpartyAddress());
        } else {
            textDogeSendingAddress.setText("Not set");
        }
        
        // Set receiving address (BTC/LTC)
        if (swap.getMyReceivingAddress() != null && !swap.getMyReceivingAddress().isEmpty()) {
            String toCurrency = swap.getToCurrency();
            textReceivingAddressLabel.setText(toCurrency + " Receiving Address");
            textReceivingAddress.setText(swap.getMyReceivingAddress());
            textReceivingAddressLabel.setVisibility(View.VISIBLE);
            textReceivingAddress.setVisibility(View.VISIBLE);
        } else {
            textReceivingAddressLabel.setVisibility(View.GONE);
            textReceivingAddress.setVisibility(View.GONE);
        }
        
        // Set transaction ID (Dogecoin contract transaction)
        if (swap.getDogecoinTxId() != null && !swap.getDogecoinTxId().isEmpty()) {
            textTxId.setText(swap.getDogecoinTxId());
            textTxIdLabel.setVisibility(View.VISIBLE);
            layoutTxId.setVisibility(View.VISIBLE);
        } else {
            textTxIdLabel.setVisibility(View.GONE);
            layoutTxId.setVisibility(View.GONE);
        }
        
        AlertDialog dialog = builder.create();
        
        // Apply proper dialog styling with square corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background_square);
        }
        
        // Copy secret hash
        btnCopyHash.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Secret Hash", swap.getSecretHash());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Secret hash copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        
        // Copy transaction ID
        btnCopyTxId.setOnClickListener(v -> {
            if (swap.getDogecoinTxId() != null && !swap.getDogecoinTxId().isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Transaction ID", swap.getDogecoinTxId());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Transaction ID copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private String formatAmount(long amount, String currency) {
        if ("DOGE".equals(currency)) {
            // Use toPlainString() to get just the number without currency suffix
            // This avoids issues with toFriendlyString() which includes "DOGE" suffix
            Coin coin = Coin.valueOf(amount);
            return coin.toPlainString();
        } else if ("BTC".equals(currency)) {
            return String.format(Locale.US, "%.8f", amount / 100000000.0);
        } else if ("LTC".equals(currency)) {
            return String.format(Locale.US, "%.8f", amount / 100000000.0);
        }
        return String.valueOf(amount);
    }
    
    private class AtomicSwapsAdapter extends RecyclerView.Adapter<AtomicSwapsAdapter.SwapViewHolder> {
        @NonNull
        @Override
        public SwapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_atomic_swap, parent, false);
            return new SwapViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull SwapViewHolder holder, int position) {
            AtomicSwap swap = swaps.get(position);
            holder.bind(swap);
        }
        
        @Override
        public int getItemCount() {
            return swaps.size();
        }
        
        class SwapViewHolder extends RecyclerView.ViewHolder {
            private TextView textSwapDirection;
            private TextView textSwapStatus;
            private TextView textFromAmount;
            private TextView textToAmount;
            private TextView textSecretHash;
            private TextView textTimestamp;
            private ImageButton btnCopyHash;
            private Button btnViewDetails;
            private Button btnClaim;
            private Button btnRefund;
            private ImageButton btnDelete;
            
            SwapViewHolder(View itemView) {
                super(itemView);
                textSwapDirection = itemView.findViewById(R.id.text_swap_direction);
                textSwapStatus = itemView.findViewById(R.id.text_swap_status);
                textFromAmount = itemView.findViewById(R.id.text_from_amount);
                textToAmount = itemView.findViewById(R.id.text_to_amount);
                textSecretHash = itemView.findViewById(R.id.text_secret_hash);
                textTimestamp = itemView.findViewById(R.id.text_timestamp);
                btnCopyHash = itemView.findViewById(R.id.btn_copy_hash);
                btnViewDetails = itemView.findViewById(R.id.btn_view_details);
                btnClaim = itemView.findViewById(R.id.btn_claim);
                btnRefund = itemView.findViewById(R.id.btn_refund);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
            
            void bind(AtomicSwap swap) {
                textSwapDirection.setText(swap.getFromCurrency() + " → " + swap.getToCurrency());
                textSwapStatus.setText(swap.getStatus());
                // Format amount (formatAmount returns just the number, so we add the currency)
                textFromAmount.setText(formatAmount(swap.getFromAmount(), swap.getFromCurrency()) + " " + swap.getFromCurrency());
                textToAmount.setText(formatAmount(swap.getToAmount(), swap.getToCurrency()) + " " + swap.getToCurrency());
                textSecretHash.setText(swap.getSecretHash());
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                textTimestamp.setText("Created: " + sdf.format(new Date(swap.getCreatedAt())));
                
                btnCopyHash.setOnClickListener(v -> {
                    ClipboardManager clipboard = (ClipboardManager) AtomicSwapActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Secret Hash", swap.getSecretHash());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(AtomicSwapActivity.this, "Secret hash copied", Toast.LENGTH_SHORT).show();
                });
                
                btnViewDetails.setOnClickListener(v -> showSwapDetails(swap));
                
                // Show/hide action buttons based on status
                if ("COUNTERPARTY_CONTRACT_CREATED".equals(swap.getStatus())) {
                    btnClaim.setVisibility(View.VISIBLE);
                    btnRefund.setVisibility(View.GONE);
                    btnDelete.setVisibility(View.GONE);
                } else if ("PENDING".equals(swap.getStatus()) || "CONTRACT_CREATED".equals(swap.getStatus())) {
                    btnClaim.setVisibility(View.GONE);
                    btnRefund.setVisibility(View.VISIBLE);
                    btnDelete.setVisibility(View.VISIBLE); // Show delete for pending swaps (testing)
                } else {
                    btnClaim.setVisibility(View.GONE);
                    btnRefund.setVisibility(View.GONE);
                    btnDelete.setVisibility(View.GONE);
                }
                
                btnClaim.setOnClickListener(v -> {
                    // Claim counterparty coins by revealing secret
                    Wallet wallet = walletActivityViewModel.wallet.getValue();
                    if (wallet == null) {
                        Toast.makeText(AtomicSwapActivity.this, "Wallet not available", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Toast.makeText(AtomicSwapActivity.this, "Claiming coins...", Toast.LENGTH_SHORT).show();
                    AtomicSwapService.claimCounterpartyCoins(AtomicSwapActivity.this, swap.getId(), wallet)
                        .thenAccept(result -> {
                            runOnUiThread(() -> {
                                Toast.makeText(AtomicSwapActivity.this, "Claim transaction initiated", Toast.LENGTH_SHORT).show();
                                loadSwaps();
                            });
                        })
                        .exceptionally(e -> {
                            runOnUiThread(() -> {
                                Toast.makeText(AtomicSwapActivity.this, "Error claiming: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                log.error("Error claiming coins", e);
                            });
                            return null;
                        });
                });
                
                btnRefund.setOnClickListener(v -> {
                    // Refund expired swap
                    String fromCurrency = swap.getFromCurrency();
                    
                    new AlertDialog.Builder(AtomicSwapActivity.this, R.style.My_Theme_Dialog)
                            .setTitle("Refund Swap")
                            .setMessage("Are you sure you want to refund this swap? This will return your locked coins.")
                            .setPositiveButton("Refund", (dialog, which) -> {
                                Toast.makeText(AtomicSwapActivity.this, "Refunding coins...", Toast.LENGTH_SHORT).show();
                                
                                if ("DOGE".equals(fromCurrency)) {
                                    Wallet wallet = walletActivityViewModel.wallet.getValue();
                                    if (wallet == null) {
                                        Toast.makeText(AtomicSwapActivity.this, "Wallet not available", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    
                                    AtomicSwapService.refundDogecoinCoins(AtomicSwapActivity.this, swap.getId(), wallet)
                                        .thenAccept(result -> {
                                            runOnUiThread(() -> {
                                                Toast.makeText(AtomicSwapActivity.this, "Refund transaction initiated", Toast.LENGTH_SHORT).show();
                                                loadSwaps();
                                            });
                                        })
                                        .exceptionally(e -> {
                                            runOnUiThread(() -> {
                                                Toast.makeText(AtomicSwapActivity.this, "Error refunding: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                log.error("Error refunding coins", e);
                                            });
                                            return null;
                                        });
                                } else if ("BTC".equals(fromCurrency)) {
                                    AtomicSwapService.refundBitcoinCoins(AtomicSwapActivity.this, swap.getId())
                                        .thenAccept(result -> {
                                            runOnUiThread(() -> {
                                                if (result.startsWith("REFUND_DATA_CREATED:")) {
                                                    Toast.makeText(AtomicSwapActivity.this, result.substring("REFUND_DATA_CREATED:".length()), Toast.LENGTH_LONG).show();
                                                } else {
                                                    Toast.makeText(AtomicSwapActivity.this, "Refund transaction initiated", Toast.LENGTH_SHORT).show();
                                                }
                                                loadSwaps();
                                            });
                                        })
                                        .exceptionally(e -> {
                                            runOnUiThread(() -> {
                                                Toast.makeText(AtomicSwapActivity.this, "Error refunding: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                log.error("Error refunding coins", e);
                                            });
                                            return null;
                                        });
                                } else if ("LTC".equals(fromCurrency)) {
                                    AtomicSwapService.refundLitecoinCoins(AtomicSwapActivity.this, swap.getId())
                                        .thenAccept(result -> {
                                            runOnUiThread(() -> {
                                                if (result.startsWith("REFUND_DATA_CREATED:")) {
                                                    Toast.makeText(AtomicSwapActivity.this, result.substring("REFUND_DATA_CREATED:".length()), Toast.LENGTH_LONG).show();
                                                } else {
                                                    Toast.makeText(AtomicSwapActivity.this, "Refund transaction initiated", Toast.LENGTH_SHORT).show();
                                                }
                                                loadSwaps();
                                            });
                                        })
                                        .exceptionally(e -> {
                                            runOnUiThread(() -> {
                                                Toast.makeText(AtomicSwapActivity.this, "Error refunding: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                log.error("Error refunding coins", e);
                                            });
                                            return null;
                                        });
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
                
                btnDelete.setOnClickListener(v -> {
                    // Delete swap (for testing purposes)
                    new AlertDialog.Builder(AtomicSwapActivity.this, R.style.My_Theme_Dialog)
                            .setTitle("Delete Swap")
                            .setMessage("Are you sure you want to delete this swap? This action cannot be undone.")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                swapDao.deleteAtomicSwap(swap);
                                Toast.makeText(AtomicSwapActivity.this, "Swap deleted", Toast.LENGTH_SHORT).show();
                                loadSwaps();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }
        }
    }
}
