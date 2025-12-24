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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.drawable.Drawable;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.annotation.WorkerThread;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.wallet.R;
import de.schildbach.wallet.data.Check;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookDao;
import de.schildbach.wallet.util.ExcludedAddressHelper;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.DeterministicKeyChain;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.ui.AbstractWalletActivityViewModel;
import de.schildbach.wallet.ui.send.FeeCategory;
import de.schildbach.wallet.data.DynamicFeeLiveData;
import java.util.Map;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity for writing and managing Dogecoin checks (timelock transactions)
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class WriteCheckActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(WriteCheckActivity.class);
    
    private RecyclerView recyclerChecks;
    private TextView textEmptyState;
    private Button btnCreateCheck;
    
    private CheckAdapter adapter;
    private List<Check> checks;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_check);
        
        checks = new ArrayList<>();
        
        initializeViews();
        setupRecyclerView();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Always reload checks when activity resumes to ensure fresh database access
        loadChecks();
    }
    
    private void initializeViews() {
        recyclerChecks = findViewById(R.id.recycler_checks);
        textEmptyState = findViewById(R.id.text_empty_state);
        btnCreateCheck = findViewById(R.id.btn_create_check);
        
        btnCreateCheck.setOnClickListener(v -> showCreateCheckDialog());
    }
    
    private void setupRecyclerView() {
        adapter = new CheckAdapter(checks);
        recyclerChecks.setLayoutManager(new LinearLayoutManager(this));
        recyclerChecks.setAdapter(adapter);
    }
    
    private void loadChecks() {
        new Thread(() -> {
            try {
                // Initialize BitcoinJ context for wallet operations
                org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                
                // Always get a fresh database instance to avoid closed database issues
                AddressBookDatabase db = AddressBookDatabase.getDatabase(WriteCheckActivity.this);
                
                List<Check> allChecks = null;
                try {
                    // Check if database is open
                    if (!db.isOpen()) {
                        log.error("Database is not open!");
                        runOnUiThread(() -> {
                            Toast.makeText(WriteCheckActivity.this, "Database is not open", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                    
                    allChecks = db.checkDao().getAllChecks();
                    if (allChecks == null) {
                        allChecks = new ArrayList<>();
                    }
                } catch (android.database.sqlite.SQLiteException e) {
                    log.error("Database corruption detected: {}", e.getMessage(), e);
                    // Clear the database instance to force recreation
                    AddressBookDatabase.clearInstance();
                    runOnUiThread(() -> {
                        Toast.makeText(WriteCheckActivity.this, "Database error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                    return;
                } catch (IllegalStateException e) {
                    log.error("Database access error: {}", e.getMessage(), e);
                    // Clear the database instance to force recreation
                    AddressBookDatabase.clearInstance();
                    // Try to get a fresh database instance
                    try {
                        db = AddressBookDatabase.getDatabase(WriteCheckActivity.this);
                        allChecks = db.checkDao().getAllChecks();
                        if (allChecks == null) {
                            allChecks = new ArrayList<>();
                        }
                    } catch (Exception e2) {
                        log.error("Failed to recover from database error", e2);
                        runOnUiThread(() -> {
                            Toast.makeText(WriteCheckActivity.this, "Failed to load checks: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                }
                
                final List<Check> finalAllChecks = allChecks;
                
                // Add all check addresses to wallet's watched scripts (for old checks that weren't added during creation)
                WalletApplication app = (WalletApplication) getApplicationContext();
                Wallet wallet = app.getWallet();
                if (wallet != null) {
                    for (Check check : finalAllChecks) {
                        String checkAddressStr = check.getAddress();
                        if (checkAddressStr != null && !checkAddressStr.trim().isEmpty()) {
                            try {
                                Address checkAddress = LegacyAddress.fromBase58(Constants.NETWORK_PARAMETERS, checkAddressStr);
                                Script addressScript = ScriptBuilder.createOutputScript(checkAddress);
                                
                                // Try to add to watched scripts/addresses
                                if (addressScript.isPayToScriptHash()) {
                                    // For P2SH, add the output script
                                    try {
                                        java.lang.reflect.Method addWatchedScriptMethod = wallet.getClass().getMethod("addWatchedScript", Script.class);
                                        addWatchedScriptMethod.invoke(wallet, addressScript);
                                        log.debug("Added existing check address to wallet watched scripts: {}", checkAddressStr);
                                    } catch (NoSuchMethodException e) {
                                        // Fallback: try addWatchedAddress
                                        try {
                                            java.lang.reflect.Method addWatchedAddressMethod = wallet.getClass().getMethod("addWatchedAddress", Address.class);
                                            addWatchedAddressMethod.invoke(wallet, checkAddress);
                                            log.debug("Added existing check address to wallet watched addresses: {}", checkAddressStr);
                                        } catch (NoSuchMethodException e2) {
                                            // Method not available, skip
                                        }
                                    }
                                } else {
                                    // For non-P2SH, try addWatchedAddress
                                    try {
                                        java.lang.reflect.Method addWatchedAddressMethod = wallet.getClass().getMethod("addWatchedAddress", Address.class);
                                        addWatchedAddressMethod.invoke(wallet, checkAddress);
                                        log.debug("Added existing check address to wallet watched addresses: {}", checkAddressStr);
                                    } catch (NoSuchMethodException e) {
                                        // Method not available, skip
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Error adding check address to wallet watched list: {}", e.getMessage());
                            }
                        }
                    }
                }
                
                runOnUiThread(() -> {
                    checks.clear();
                    checks.addAll(finalAllChecks);
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });
            } catch (Exception e) {
                log.error("Error loading checks", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading checks", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void updateEmptyState() {
        if (checks.isEmpty()) {
            recyclerChecks.setVisibility(View.GONE);
            textEmptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerChecks.setVisibility(View.VISIBLE);
            textEmptyState.setVisibility(View.GONE);
        }
    }
    
    private void showCreateCheckDialog() {
        CreateCheckDialogFragment dialog = new CreateCheckDialogFragment();
        dialog.setOnCheckCreatedListener(new CreateCheckDialogFragment.OnCheckCreatedListener() {
            @Override
            public void onCheckCreated(Check check) {
                loadChecks();
                Toast.makeText(WriteCheckActivity.this, R.string.write_check_created, Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show(getSupportFragmentManager(), "CreateCheckDialog");
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.write_check_options, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private class CheckAdapter extends RecyclerView.Adapter<CheckAdapter.CheckViewHolder> {
        private final List<Check> checks;
        
        public CheckAdapter(List<Check> checks) {
            this.checks = checks;
        }
        
        @NonNull
        @Override
        public CheckViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_check, parent, false);
            return new CheckViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull CheckViewHolder holder, int position) {
            Check check = checks.get(position);
            holder.bind(check);
        }
        
        @Override
        public int getItemCount() {
            return checks.size();
        }
        
        class CheckViewHolder extends RecyclerView.ViewHolder {
            private TextView textPayTo;
            private TextView textAmount;
            private TextView textDate;
            private TextView textExpirationDate;
            private TextView textMemo;
            private TextView textSignature;
            private TextView textTransactionId;
            private TextView textStatusBadge;
            private View layoutTransactionId;
            private View layoutExpandedContent;
            private View layoutExpirationDate;
            private Button btnPrint;
            private Button btnViewTransaction;
            private Button btnCancel;
            private Button btnShare;
            private boolean isExpanded = false;
            
            public CheckViewHolder(@NonNull View itemView) {
                super(itemView);
                textPayTo = itemView.findViewById(R.id.text_pay_to);
                textAmount = itemView.findViewById(R.id.text_amount);
                textDate = itemView.findViewById(R.id.text_date);
                textExpirationDate = itemView.findViewById(R.id.text_expiration_date);
                textMemo = itemView.findViewById(R.id.text_memo);
                textSignature = itemView.findViewById(R.id.text_signature);
                textTransactionId = itemView.findViewById(R.id.text_transaction_id);
                textStatusBadge = itemView.findViewById(R.id.text_status_badge);
                layoutTransactionId = itemView.findViewById(R.id.layout_transaction_id);
                layoutExpandedContent = itemView.findViewById(R.id.layout_expanded_content);
                layoutExpirationDate = itemView.findViewById(R.id.layout_expiration_date);
                btnPrint = itemView.findViewById(R.id.btn_print);
                btnViewTransaction = itemView.findViewById(R.id.btn_view_transaction);
                btnCancel = itemView.findViewById(R.id.btn_cancel);
                btnShare = itemView.findViewById(R.id.btn_share);
                
                // Make buttons consume touch events to prevent parent click
                View.OnTouchListener consumeTouch = (v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return false; // Let the button handle the click normally
                };
                btnPrint.setOnTouchListener(consumeTouch);
                btnViewTransaction.setOnTouchListener(consumeTouch);
                btnCancel.setOnTouchListener(consumeTouch);
                btnShare.setOnTouchListener(consumeTouch);
                
                // Make the entire card clickable to expand/collapse
                itemView.setOnClickListener(v -> {
                    isExpanded = !isExpanded;
                    layoutExpandedContent.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                });
            }
            
            public void bind(Check check) {
                textPayTo.setText(check.getPayTo());
                
                Coin amount = Coin.valueOf(check.getAmount());
                textAmount.setText(amount.toPlainString() + " DOGE");
                
                // Date with time format
                SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                textDate.setText(dateTimeFormat.format(check.getDate()));
                
                // Expiration date with time
                if (check.getExpirationDate() != null) {
                    layoutExpirationDate.setVisibility(View.VISIBLE);
                    textExpirationDate.setText(dateTimeFormat.format(check.getExpirationDate()));
                } else {
                    layoutExpirationDate.setVisibility(View.GONE);
                }
                
                // Memo (shown when collapsed)
                String memo = check.getMemo();
                if (memo != null && !memo.trim().isEmpty()) {
                    textMemo.setVisibility(View.VISIBLE);
                    textMemo.setText(memo);
                } else {
                    textMemo.setVisibility(View.GONE);
                }
                
                // Signature (moved to expanded section)
                textSignature.setText(check.getSignature());
                
                // Check balance using wallet's internal state (mempool/headers tracking)
                // This runs on a background thread to avoid blocking UI
                new Thread(() -> {
                    try {
                        String checkAddressStr = check.getAddress();
                        if (checkAddressStr == null || checkAddressStr.trim().isEmpty()) {
                            return;
                        }
                        
                        WalletApplication app = (WalletApplication) WriteCheckActivity.this.getApplicationContext();
                        Wallet wallet = app.getWallet();
                        if (wallet == null) {
                            return;
                        }
                        
                        Address checkAddress = LegacyAddress.fromBase58(Constants.NETWORK_PARAMETERS, checkAddressStr);
                        Script addressScript = ScriptBuilder.createOutputScript(checkAddress);
                        Coin balance = Coin.ZERO;
                        boolean balanceFromWallet = false;
                        
                        // Track if we found the check output in wallet data (even if spent)
                        boolean foundCheckOutput = false;
                        int checkOutputIndex = -1;
                        Coin checkOutputValue = Coin.ZERO;
                        
                        // First, try to get balance from wallet's internal state
                        String checkTxHash = check.getTransactionHash();
                        if (checkTxHash != null && !checkTxHash.trim().isEmpty()) {
                            try {
                                Sha256Hash checkTxId = Sha256Hash.wrap(checkTxHash);
                                Transaction checkTx = wallet.getTransaction(checkTxId);
                                
                                if (checkTx != null) {
                                    // Check each output of the check creation transaction
                                    for (int i = 0; i < checkTx.getOutputs().size(); i++) {
                                        TransactionOutput output = checkTx.getOutput(i);
                                        Script outputScript = output.getScriptPubKey();
                                        
                                        // Check if this output is to our P2SH address
                                        boolean isCheckOutput = false;
                                        if (outputScript.isPayToScriptHash() && addressScript.isPayToScriptHash()) {
                                            // For P2SH, compare script hashes (getPubKeyHash() returns script hash for P2SH)
                                            byte[] outputHash = outputScript.getPubKeyHash();
                                            byte[] addressHash = addressScript.getPubKeyHash();
                                            if (java.util.Arrays.equals(outputHash, addressHash)) {
                                                isCheckOutput = true;
                                            }
                                        } else if (outputScript.equals(addressScript)) {
                                            isCheckOutput = true;
                                        }
                                        
                                        if (isCheckOutput) {
                                            foundCheckOutput = true;
                                            checkOutputIndex = i;
                                            checkOutputValue = output.getValue();
                                            
                                            // Check if this output is still unspent
                                            boolean isSpent = false;
                                            for (Transaction walletTx : wallet.getTransactions(false)) {
                                                for (TransactionInput input : walletTx.getInputs()) {
                                                    if (input.getOutpoint().getHash().equals(checkTxId) && 
                                                        input.getOutpoint().getIndex() == i) {
                                                        isSpent = true;
                                                        break;
                                                    }
                                                }
                                                if (isSpent) break;
                                            }
                                            
                                            if (!isSpent) {
                                                balance = balance.add(output.getValue());
                                                balanceFromWallet = true;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Error checking check transaction for balance: {}", e.getMessage());
                            }
                        }
                        
                        // Also check wallet.getUnspents() for the check address
                        List<TransactionOutput> unspentOutputs = wallet.getUnspents();
                        for (TransactionOutput output : unspentOutputs) {
                            Script outputScript = output.getScriptPubKey();
                            if (outputScript.isPayToScriptHash() && addressScript.isPayToScriptHash()) {
                                // For P2SH, compare script hashes (getPubKeyHash() returns script hash for P2SH)
                                byte[] outputHash = outputScript.getPubKeyHash();
                                byte[] addressHash = addressScript.getPubKeyHash();
                                if (java.util.Arrays.equals(outputHash, addressHash)) {
                                    balance = balance.add(output.getValue());
                                    balanceFromWallet = true;
                                    foundCheckOutput = true; // Found in unspents, so we know it exists
                                }
                            } else if (outputScript.equals(addressScript)) {
                                balance = balance.add(output.getValue());
                                balanceFromWallet = true;
                                foundCheckOutput = true; // Found in unspents, so we know it exists
                            }
                        }
                        
                        // If we found the check output in wallet data but balance is zero, it must have been spent
                        // This handles the case where check was swept on another wallet
                        if (foundCheckOutput && balance.isZero()) {
                            balanceFromWallet = true; // We have wallet data, just balance is zero (spent)
                            log.info("Check output found in wallet data but balance is zero - check has been spent");
                        }
                        
                        // Use only wallet's internal state (mempool/headers tracking)
                        // Only update status if we have wallet data
                        if (!balanceFromWallet) {
                            // Wallet doesn't have data yet - this can happen for old checks that weren't tracked
                            // They will be added to watched scripts on next load, so balance will be available after sync
                            log.debug("Check address {} not yet tracked by wallet (will be tracked after sync)", checkAddressStr);
                            return; // Skip status update if wallet doesn't have data
                        }
                        
                        log.info("Check address {} balance from wallet: {} DOGE", 
                            checkAddressStr, balance.toPlainString());
                        
                        // If balance is zero, check if funds were swept to wallet address (canceled) or external (deposited)
                        if (balance.isZero()) {
                            boolean wasSweptToWallet = false;
                            
                            // Check if there's a transaction that spent from the check address and sent to wallet
                            if (checkTxHash != null && !checkTxHash.trim().isEmpty()) {
                                try {
                                    Sha256Hash checkTxId = Sha256Hash.wrap(checkTxHash);
                                    
                                    // Look through wallet transactions to find one that spent from the check
                                    for (Transaction walletTx : wallet.getTransactions(false)) {
                                        // Check if this transaction has inputs that spend from the check creation transaction
                                        for (TransactionInput input : walletTx.getInputs()) {
                                            if (input.getOutpoint().getHash().equals(checkTxId)) {
                                                // This transaction spends from the check - check if output goes to wallet
                                                for (TransactionOutput output : walletTx.getOutputs()) {
                                                    if (output.isMine(wallet)) {
                                                        // Funds were swept to wallet address - this is a cancellation
                                                        wasSweptToWallet = true;
                                                        log.info("Check {} was swept to wallet address - marking as canceled", check.getId());
                                                        break;
                                                    }
                                                }
                                                if (wasSweptToWallet) break;
                                            }
                                        }
                                        if (wasSweptToWallet) break;
                                    }
                                } catch (Exception e) {
                                    log.warn("Error checking if check was swept to wallet: {}", e.getMessage());
                                }
                            }
                            
                            // Update status based on whether it was swept to wallet or external
                            // If foundCheckOutput is true but wasSweptToWallet is false, it was deposited externally
                            String newStatus = wasSweptToWallet ? "canceled" : "deposited";
                            if (!newStatus.equals(check.getStatus())) {
                                check.setStatus(newStatus);
                                try {
                                    AddressBookDatabase db = AddressBookDatabase.getDatabase(WriteCheckActivity.this);
                                    if (db != null && db.isOpen()) {
                                        db.checkDao().updateCheck(check);
                                        
                                        // Add address book entry for the transaction
                                        // Find the transaction that spent from the check
                                        Transaction spendingTx = null;
                                        if (checkTxHash != null && !checkTxHash.trim().isEmpty()) {
                                            try {
                                                Sha256Hash checkTxId = Sha256Hash.wrap(checkTxHash);
                                                for (Transaction walletTx : wallet.getTransactions(false)) {
                                                    for (TransactionInput input : walletTx.getInputs()) {
                                                        if (input.getOutpoint().getHash().equals(checkTxId)) {
                                                            spendingTx = walletTx;
                                                            break;
                                                        }
                                                    }
                                                    if (spendingTx != null) break;
                                                }
                                            } catch (Exception e) {
                                                log.warn("Error finding spending transaction: {}", e.getMessage());
                                            }
                                        }
                                        
                                        // Add address book entry with check label
                                        if (spendingTx != null) {
                                            try {
                                                AddressBookDao addressBookDao = db.addressBookDao();
                                                String payTo = check.getPayTo() != null ? check.getPayTo() : "Unknown";
                                                String label = (newStatus.equals("canceled") ? "Canceled" : "Deposited") + " - " + payTo;
                                                
                                                // Get the output address from the spending transaction
                                                Address txAddress = null;
                                                if (wasSweptToWallet) {
                                                    // For canceled checks, get the return address (first output that's mine)
                                                    for (TransactionOutput output : spendingTx.getOutputs()) {
                                                        if (output.isMine(wallet)) {
                                                            txAddress = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                                                            break;
                                                        }
                                                    }
                                                } else {
                                                    // For deposited checks, get the first external output address
                                                    for (TransactionOutput output : spendingTx.getOutputs()) {
                                                        if (!output.isMine(wallet)) {
                                                            txAddress = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                                                            break;
                                                        }
                                                    }
                                                }
                                                
                                                if (txAddress != null) {
                                                    addressBookDao.insertOrUpdate(new AddressBookEntry(txAddress.toString(), label));
                                                    log.info("Added address book entry for check {}: {} -> {}", check.getId(), txAddress.toString(), label);
                                                }
                                            } catch (Exception e) {
                                                log.warn("Error adding address book entry for check: {}", e.getMessage());
                                            }
                                        }
                                        
                                        // Reload checks on main thread
                                        runOnUiThread(() -> loadChecks());
                                    } else {
                                        log.warn("Database not available for status update");
                                    }
                                } catch (Exception e) {
                                    log.error("Error updating check status: {}", e.getMessage(), e);
                                }
                            }
                        } else if (!balance.isZero() && ("deposited".equals(check.getStatus()) || "canceled".equals(check.getStatus()))) {
                            // If balance is not zero but status is "deposited" or "canceled", revert to "active"
                            check.setStatus("active");
                            try {
                                AddressBookDatabase db = AddressBookDatabase.getDatabase(WriteCheckActivity.this);
                                if (db != null && db.isOpen()) {
                                    db.checkDao().updateCheck(check);
                                    // Reload checks on main thread
                                    runOnUiThread(() -> loadChecks());
                                } else {
                                    log.warn("Database not available for status update");
                                }
                            } catch (Exception e) {
                                log.error("Error updating check status: {}", e.getMessage(), e);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error checking check balance: {}", e.getMessage());
                    }
                }).start();
                
                // Show status badge
                String status = check.getStatus();
                if (status != null && !status.isEmpty()) {
                    textStatusBadge.setVisibility(View.VISIBLE);
                    String statusText = status.substring(0, 1).toUpperCase() + status.substring(1);
                    textStatusBadge.setText(statusText);
                    
                    // Set badge color based on status (using GradientDrawable to preserve rounded corners)
                    android.graphics.drawable.GradientDrawable badgeDrawable = new android.graphics.drawable.GradientDrawable();
                    badgeDrawable.setCornerRadius(12 * itemView.getResources().getDisplayMetrics().density); // 12dp in pixels
                    if ("active".equals(status)) {
                        badgeDrawable.setColor(0xFF4CAF50); // Green
                    } else if ("canceled".equals(status)) {
                        badgeDrawable.setColor(0xFFF44336); // Red
                    } else if ("spent".equals(status)) {
                        badgeDrawable.setColor(0xFF2196F3); // Blue
                    } else if ("deposited".equals(status)) {
                        badgeDrawable.setColor(0xFF9E9E9E); // Gray
                    } else {
                        badgeDrawable.setColor(0xFF9E9E9E); // Gray
                    }
                    textStatusBadge.setBackground(badgeDrawable);
                } else {
                    textStatusBadge.setVisibility(View.GONE);
                }
                
                // Set button icon colors based on theme (black in light mode, white in dark mode)
                int nightModeFlags = itemView.getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                boolean isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                int iconTintColor = isDarkMode ? 
                    ContextCompat.getColor(itemView.getContext(), android.R.color.white) : 
                    ContextCompat.getColor(itemView.getContext(), android.R.color.black);
                
                // Tint button icons
                if (btnPrint != null) {
                    Drawable printIcon = ContextCompat.getDrawable(itemView.getContext(), R.drawable.ic_file_white_24dp);
                    if (printIcon != null) {
                        printIcon.setTint(iconTintColor);
                        btnPrint.setCompoundDrawablesWithIntrinsicBounds(printIcon, null, null, null);
                    }
                }
                if (btnViewTransaction != null) {
                    Drawable qrIcon = ContextCompat.getDrawable(itemView.getContext(), R.drawable.ic_qrcode_white_24dp);
                    if (qrIcon != null) {
                        qrIcon.setTint(iconTintColor);
                        btnViewTransaction.setCompoundDrawablesWithIntrinsicBounds(qrIcon, null, null, null);
                    }
                }
                if (btnCancel != null) {
                    Drawable closeIcon = ContextCompat.getDrawable(itemView.getContext(), R.drawable.ic_close_white_24dp);
                    if (closeIcon != null) {
                        closeIcon.setTint(iconTintColor);
                        btnCancel.setCompoundDrawablesWithIntrinsicBounds(closeIcon, null, null, null);
                    }
                }
                if (btnShare != null) {
                    Drawable shareIcon = ContextCompat.getDrawable(itemView.getContext(), R.drawable.ic_share_white_24dp);
                    if (shareIcon != null) {
                        shareIcon.setTint(iconTintColor);
                        btnShare.setCompoundDrawablesWithIntrinsicBounds(shareIcon, null, null, null);
                    }
                }
                
                // Reset expanded state
                isExpanded = false;
                layoutExpandedContent.setVisibility(View.GONE);
                
                // Show transaction ID if available (inside expanded content)
                String txHash = check.getTransactionHash();
                if (txHash != null && !txHash.trim().isEmpty()) {
                    layoutTransactionId.setVisibility(View.VISIBLE);
                    btnViewTransaction.setVisibility(View.VISIBLE);
                    // Show first 8 and last 8 characters of transaction hash
                    String shortHash = txHash.length() > 16 
                        ? txHash.substring(0, 8) + "..." + txHash.substring(txHash.length() - 8)
                        : txHash;
                    textTransactionId.setText(shortHash);
                    btnViewTransaction.setOnClickListener(v -> viewTransaction(check));
                } else {
                    layoutTransactionId.setVisibility(View.GONE);
                    btnViewTransaction.setVisibility(View.GONE);
                }
                
                // Show cancel button for:
                // 1. Active checks that haven't been spent AND timelock has been reached
                // 2. Canceled checks that still have funds (allow re-canceling if transaction wasn't created) AND timelock has been reached
                // Do NOT show cancel button for "Deposited" status
                // Do NOT show cancel button if timelock hasn't been reached (can't cancel before timelock)
                boolean isActive = "active".equals(status) && !check.isSpent();
                boolean isCanceledButRetryable = "canceled".equals(status) && !check.isSpent();
                boolean isDeposited = "deposited".equals(status);
                
                // Check if timelock has been reached
                boolean timelockReached = false;
                if (check.getDate() != null) {
                    long locktime = check.getDate().getTime() / 1000; // Unix timestamp in seconds
                    long currentTime = System.currentTimeMillis() / 1000;
                    timelockReached = currentTime >= locktime;
                }
                
                if ((isActive || isCanceledButRetryable) && !isDeposited && timelockReached) {
                    btnCancel.setVisibility(View.VISIBLE);
                    btnCancel.setOnClickListener(v -> cancelCheck(check));
                } else {
                    btnCancel.setVisibility(View.GONE);
                }
                
                btnPrint.setOnClickListener(v -> printCheck(check));
                btnShare.setOnClickListener(v -> shareCheck(check));
            }
            
            private void printCheck(Check check) {
                // Generate PDF with check design
                try {
                    CheckPdfGenerator.generatePdf(WriteCheckActivity.this, check);
                } catch (Exception e) {
                    Toast.makeText(WriteCheckActivity.this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            
            private void viewTransaction(Check check) {
                // View the check wallet address (P2SH CLTV address) instead of transaction hash
                String checkAddress = check.getAddress();
                if (checkAddress == null || checkAddress.trim().isEmpty()) {
                    Toast.makeText(WriteCheckActivity.this, "Check address not available", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                try {
                    WalletApplication app = (WalletApplication) getApplicationContext();
                    de.schildbach.wallet.Configuration config = app.getConfiguration();
                    android.net.Uri blockExplorerUri = android.net.Uri.parse(String.format(config.getBlockExplorer(), "address"));
                    log.info("Viewing check address {} on {}", checkAddress, blockExplorerUri);
                    startExternalDocument(android.net.Uri.withAppendedPath(blockExplorerUri, checkAddress));
                } catch (Exception e) {
                    log.error("Error viewing check address", e);
                    Toast.makeText(WriteCheckActivity.this, "Error opening address: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            
            private void cancelCheck(Check check) {
                // Show confirmation dialog
                new AlertDialog.Builder(WriteCheckActivity.this)
                    .setTitle(R.string.write_check_cancel)
                    .setMessage(R.string.write_check_cancel_confirm)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        // Cancel check on background thread
                        new Thread(() -> {
                            try {
                                runOnUiThread(() -> {
                                    Toast.makeText(WriteCheckActivity.this, R.string.write_check_canceling, Toast.LENGTH_SHORT).show();
                                });
                                
                                String errorMessage = cancelCheckInternal(check);
                                
                                runOnUiThread(() -> {
                                    if (errorMessage == null) {
                                        // Success
                                        Toast.makeText(WriteCheckActivity.this, R.string.write_check_canceled, Toast.LENGTH_SHORT).show();
                                        loadChecks(); // Reload checks to update UI
                                    } else {
                                        // Error occurred - show detailed message
                                        new AlertDialog.Builder(WriteCheckActivity.this)
                                            .setTitle(R.string.write_check_cancel_error)
                                            .setMessage(errorMessage)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                        loadChecks(); // Reload checks to update UI (transaction is still queued)
                                    }
                                });
                            } catch (Exception e) {
                                log.error("Error canceling check", e);
                                runOnUiThread(() -> {
                                    Toast.makeText(WriteCheckActivity.this, R.string.write_check_cancel_error, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }).start();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            }
            
            @WorkerThread
            private String cancelCheckInternal(Check check) {
                // Returns null on success, error message string on failure (but transaction is still queued)
                try {
                    org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                    
                    WalletApplication app = (WalletApplication) getApplicationContext();
                    Wallet wallet = app.getWallet();
                    AddressBookDatabase db = AddressBookDatabase.getDatabase(WriteCheckActivity.this);
                    
                    if (wallet == null || wallet.isEncrypted()) {
                        log.error("Wallet not available or encrypted");
                        return "Wallet not available or encrypted";
                    }
                    
                    // Parse the private key from WIF format
                    DumpedPrivateKey dumpedKey = DumpedPrivateKey.fromBase58(Constants.NETWORK_PARAMETERS, check.getDerivedKey());
                    ECKey key = dumpedKey.getKey();
                    
                    // Get the check address (P2SH CLTV address)
                    Address checkAddress = LegacyAddress.fromBase58(Constants.NETWORK_PARAMETERS, check.getAddress());
                    
            // For P2SH CLTV addresses, we need to find the transaction that sent funds to the check
            // and check if those outputs are still unspent
            Script addressScript = ScriptBuilder.createOutputScript(checkAddress);
            Coin balance = Coin.ZERO;
            List<UTXO> checkUtxos = new ArrayList<>();
            
            // First, try to find the transaction that created the check (if we have the tx hash)
            String checkTxHash = check.getTransactionHash();
            if (checkTxHash != null && !checkTxHash.trim().isEmpty()) {
                try {
                    Sha256Hash txId = Sha256Hash.wrap(checkTxHash);
                    Transaction checkTx = wallet.getTransaction(txId);
                    if (checkTx != null) {
                        // Check each output of the check creation transaction
                        for (int i = 0; i < checkTx.getOutputs().size(); i++) {
                            TransactionOutput output = checkTx.getOutput(i);
                            Script outputScript = output.getScriptPubKey();
                            
                            // Check if this output is to our P2SH address
                            boolean isCheckOutput = false;
                            if (outputScript.isPayToScriptHash() && addressScript.isPayToScriptHash()) {
                                byte[] outputHash = outputScript.getPubKeyHash();
                                byte[] addressHash = addressScript.getPubKeyHash();
                                if (java.util.Arrays.equals(outputHash, addressHash)) {
                                    isCheckOutput = true;
                                }
                            } else if (outputScript.equals(addressScript)) {
                                isCheckOutput = true;
                            }
                            
                            if (isCheckOutput) {
                                // Check if this output is still unspent by checking if it's been spent
                                boolean isSpent = false;
                                for (Transaction walletTx : wallet.getTransactions(false)) {
                                    for (TransactionInput input : walletTx.getInputs()) {
                                        if (input.getOutpoint().getHash().equals(txId) && 
                                            input.getOutpoint().getIndex() == i) {
                                            isSpent = true;
                                            break;
                                        }
                                    }
                                    if (isSpent) break;
                                }
                                
                                if (!isSpent) {
                                    balance = balance.add(output.getValue());
                                    UTXO utxo = new UTXO(
                                        txId,
                                        i,
                                        output.getValue(),
                                        -1,
                                        false,
                                        output.getScriptPubKey()
                                    );
                                    checkUtxos.add(utxo);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error checking check transaction: {}", e.getMessage());
                }
            }
            
            // Also check wallet.getUnspents() as fallback (might work for some cases)
            if (balance.isZero()) {
                List<TransactionOutput> unspentOutputs = wallet.getUnspents();
                for (TransactionOutput output : unspentOutputs) {
                    Script outputScript = output.getScriptPubKey();
                    // For P2SH, compare the script hash
                    if (outputScript.isPayToScriptHash() && addressScript.isPayToScriptHash()) {
                        byte[] outputHash = outputScript.getPubKeyHash();
                        byte[] addressHash = addressScript.getPubKeyHash();
                        if (java.util.Arrays.equals(outputHash, addressHash)) {
                            balance = balance.add(output.getValue());
                            Transaction tx = output.getParentTransaction();
                            if (tx != null) {
                                UTXO utxo = new UTXO(
                                    tx.getTxId(),
                                    output.getIndex(),
                                    output.getValue(),
                                    -1,
                                    false,
                                    output.getScriptPubKey()
                                );
                                checkUtxos.add(utxo);
                            }
                        }
                    } else if (outputScript.equals(addressScript)) {
                        // Direct script match (for non-P2SH addresses)
                        balance = balance.add(output.getValue());
                        Transaction tx = output.getParentTransaction();
                        if (tx != null) {
                            UTXO utxo = new UTXO(
                                tx.getTxId(),
                                output.getIndex(),
                                output.getValue(),
                                -1,
                                false,
                                output.getScriptPubKey()
                            );
                            checkUtxos.add(utxo);
                        }
                    }
                }
            }
            
            if (balance.isZero() || checkUtxos.isEmpty()) {
                log.warn("Check address has no funds to return (or balance not yet tracked by wallet): {}", checkAddress);
                // Don't mark as canceled if we can't find funds - the balance check might be wrong
                // Try to construct the transaction anyway - it will fail naturally if there are no UTXOs
                // But first, check if the wallet has the check creation transaction
                if (checkTxHash == null || checkTxHash.trim().isEmpty()) {
                    return "Cannot cancel check: Check creation transaction not found. The check may not have been fully synced yet.";
                }
                // Continue to transaction construction - it will fail if there are no UTXOs
            }
            
            // Get the locktime from the check date
            long locktime = check.getDate().getTime() / 1000;
            long currentTime = System.currentTimeMillis() / 1000;
            
            // Check if there's already a cancellation transaction in the wallet
            // Look for pending transactions that spend from the check address
            boolean hasExistingCancelTx = false;
            for (Transaction tx : wallet.getPendingTransactions()) {
                if (tx.getLockTime() == locktime) {
                    // Check if this transaction spends from our check UTXOs
                    for (TransactionInput input : tx.getInputs()) {
                        for (UTXO checkUtxo : checkUtxos) {
                            if (input.getOutpoint().getHash().equals(checkUtxo.getHash()) &&
                                input.getOutpoint().getIndex() == checkUtxo.getIndex()) {
                                hasExistingCancelTx = true;
                                log.info("Found existing cancellation transaction for check {}: {}", 
                                    check.getId(), tx.getTxId());
                                break;
                            }
                        }
                        if (hasExistingCancelTx) break;
                    }
                    if (hasExistingCancelTx) break;
                }
            }
            
            if (hasExistingCancelTx) {
                log.info("Check {} already has a cancellation transaction, skipping creation", check.getId());
                // Still mark as canceled if not already
                if (!"canceled".equals(check.getStatus())) {
                    check.setStatus("canceled");
                    try {
                        if (db != null && db.isOpen()) {
                            db.checkDao().updateCheck(check);
                        } else {
                            log.warn("Database not available for status update");
                        }
                    } catch (Exception e) {
                        log.error("Error updating check status to canceled: {}", e.getMessage(), e);
                    }
                }
                return null; // Success
            }
            
            // Ensure we have UTXOs to spend
            if (checkUtxos.isEmpty()) {
                log.error("Cannot cancel check {}: No unspent outputs found for address {}", check.getId(), checkAddress);
                return "Cannot cancel check: No funds found to return. The check may have already been spent, or the wallet may not have synced the check address yet.";
            }
            
            log.info("Canceling check {}: returning {} DOGE from address {} (locktime: {}, current: {})", 
                check.getId(), balance.toPlainString(), checkAddress, locktime, currentTime);
            
            // Verify timelock has been reached before allowing cancel
            if (currentTime < locktime) {
                log.warn("Cannot cancel check {}: Timelock not reached yet (locktime: {}, current: {})", 
                    check.getId(), locktime, currentTime);
                return "Cannot cancel check: Timelock has not been reached yet. The check can only be canceled after " + 
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date(locktime * 1000));
            }
            
            // Create a temporary wallet with the check's private key
            Wallet checkWallet = new Wallet(Constants.NETWORK_PARAMETERS);
            checkWallet.importKey(key);
            
            // For P2SH CLTV, we need to manually construct the transaction
            // Reconstruct the CLTV script
            Script cltvScript = new ScriptBuilder()
                .number(locktime)
                .op(org.bitcoinj.script.ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                .op(org.bitcoinj.script.ScriptOpCodes.OP_DROP)
                .data(key.getPubKey())
                .op(org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG)
                .build();
            
            byte[] scriptHash = org.bitcoinj.core.Utils.sha256hash160(cltvScript.getProgram());
            
            // Create transaction manually for P2SH CLTV
            Transaction cancelTx = new Transaction(Constants.NETWORK_PARAMETERS);
            cancelTx.setLockTime(locktime); // Set transaction locktime
            
            // Calculate total input amount
            Coin totalInput = Coin.ZERO;
            for (UTXO utxo : checkUtxos) {
                totalInput = totalInput.add(utxo.getValue());
            }
            
            // Add inputs from check UTXOs first
            for (UTXO utxo : checkUtxos) {
                org.bitcoinj.core.TransactionOutPoint outPoint = new org.bitcoinj.core.TransactionOutPoint(
                    Constants.NETWORK_PARAMETERS, utxo.getIndex(), utxo.getHash());
                org.bitcoinj.core.TransactionInput input = new org.bitcoinj.core.TransactionInput(
                    Constants.NETWORK_PARAMETERS, cancelTx, new byte[] {}, outPoint, utxo.getValue());
                input.setSequenceNumber(0xFFFFFFFEL); // Enable locktime
                cancelTx.addInput(input);
            }
            
            // Calculate fee based on configured default fee category and transaction size
            // Get the default fee category from configuration (same as sweep wallet)
            Configuration config = app.getConfiguration();
            FeeCategory defaultFeeCategory = config.getDefaultFeeCategory();
            
            // Get fees map by loading DynamicFeeLiveData synchronously
            // Since we're on a background thread, we can use reflection to access loadInBackground
            Coin feePerKb;
            try {
                DynamicFeeLiveData dynamicFees = new DynamicFeeLiveData(app);
                // Use reflection to access private loadInBackground method
                java.lang.reflect.Method loadMethod = DynamicFeeLiveData.class.getDeclaredMethod("loadInBackground");
                loadMethod.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<FeeCategory, Coin> fees = (Map<FeeCategory, Coin>) loadMethod.invoke(dynamicFees);
                feePerKb = fees.get(defaultFeeCategory);
                if (feePerKb == null) {
                    // Fallback if category not found
                    feePerKb = Coin.valueOf(1000000); // Default from fees.txt
                }
            } catch (Exception e) {
                log.warn("Error loading fees, using default: {}", e.getMessage());
                // Fallback to default fee rate from fees.txt: 1000000 satoshis per KB = 0.01 DOGE per KB
                feePerKb = Coin.valueOf(1000000);
            }
            
            // Estimate transaction size for fee calculation (same as sweep wallet)
            // Base size: version (4) + locktime (4) + input count (1) + output count (1) = 10 bytes
            // Each input: prevout hash (32) + prevout index (4) + script length (1) + scriptSig (estimated 200 for P2SH CLTV) + sequence (4) = ~241 bytes
            // Each output: value (8) + script length (1) + scriptPubKey (25 for P2PKH) = 34 bytes
            int estimatedInputSize = 241; // P2SH CLTV scriptSig is larger than normal
            int estimatedOutputSize = 34; // P2PKH output
            int estimatedBaseSize = 10;
            int estimatedTxSize = estimatedBaseSize + (checkUtxos.size() * estimatedInputSize) + estimatedOutputSize;
            
            // Calculate fee: (size in bytes / 1000) * feePerKb
            // Add 20% buffer to account for actual size differences (same as sweep wallet)
            Coin fee = feePerKb.multiply(estimatedTxSize).divide(1000);
            fee = fee.add(fee.divide(5)); // Add 20% buffer
            
            Coin returnAmount = totalInput.subtract(fee);
            if (returnAmount.signum() <= 0) {
                log.warn("Insufficient funds to pay fee for check cancellation");
                check.setStatus("canceled");
                try {
                    if (db != null && db.isOpen()) {
                        db.checkDao().updateCheck(check);
                    } else {
                        log.warn("Database not available for status update");
                    }
                } catch (Exception e) {
                    log.error("Error updating check status to canceled: {}", e.getMessage(), e);
                }
                return "Insufficient funds to pay transaction fee";
            }
            
            // Add output to return address
            Address returnAddress = wallet.freshReceiveAddress();
            cancelTx.addOutput(returnAmount, ScriptBuilder.createOutputScript(returnAddress));
            
            // Add address book entry for canceled check transaction
            try {
                String payTo = check.getPayTo() != null ? check.getPayTo() : "Unknown";
                String label = "Canceled - " + payTo;
                AddressBookDao addressBookDao = db.addressBookDao();
                addressBookDao.insertOrUpdate(new AddressBookEntry(returnAddress.toString(), label));
                log.info("Added address book entry for canceled check {}: {} -> {}", check.getId(), returnAddress.toString(), label);
            } catch (Exception e) {
                log.warn("Error adding address book entry for canceled check: {}", e.getMessage());
            }
            
            // Sign inputs with CLTV redeem script
            for (int i = 0; i < cancelTx.getInputs().size(); i++) {
                org.bitcoinj.core.TransactionInput input = cancelTx.getInput(i);
                UTXO connectedUtxo = checkUtxos.get(i);
                
                // Sign the transaction hash
                Sha256Hash hash = cancelTx.hashForSignature(i, cltvScript, Transaction.SigHash.ALL, false);
                org.bitcoinj.core.ECKey.ECDSASignature signature = key.sign(hash);
                
                // Create scriptSig: <signature with SIGHASH_ALL> <redeemScript>
                byte[] signatureBytes = signature.encodeToDER();
                byte[] signatureWithHashType = new byte[signatureBytes.length + 1];
                System.arraycopy(signatureBytes, 0, signatureWithHashType, 0, signatureBytes.length);
                signatureWithHashType[signatureBytes.length] = (byte) Transaction.SigHash.ALL.value;
                
                Script scriptSig = new ScriptBuilder()
                    .data(signatureWithHashType)
                    .data(cltvScript.getProgram())
                    .build();
                
                input.setScriptSig(scriptSig);
            }
            
            // Verify transaction
            try {
                cancelTx.verify();
                log.info("Check cancellation transaction verified successfully: {}", cancelTx.getTxId());
            } catch (Exception verifyEx) {
                log.error("Check cancellation transaction verification failed: {}", verifyEx.getMessage(), verifyEx);
                return "Transaction verification failed: " + verifyEx.getMessage();
            }
            
            // Add transaction to main wallet (so it appears in transaction list)
            // This ensures the transaction is queued even if immediate broadcast fails
            try {
                wallet.receivePending(cancelTx, null);
                log.info("Check cancellation transaction added to wallet: {} (locktime: {}, current: {}). Will be broadcast when locktime is reached.", 
                    cancelTx.getTxId(), locktime, currentTime);
            } catch (Exception receiveEx) {
                log.error("Failed to add check cancellation transaction to wallet: {}", receiveEx.getMessage(), receiveEx);
                return "Failed to add transaction to wallet: " + receiveEx.getMessage();
            }
            
            // Verify the transaction is actually in the wallet's pending transactions queue
            boolean transactionInQueue = false;
            for (Transaction tx : wallet.getPendingTransactions()) {
                if (tx.getTxId().equals(cancelTx.getTxId())) {
                    transactionInQueue = true;
                    log.info("Verified cancellation transaction {} is in wallet pending queue", cancelTx.getTxId());
                    break;
                }
            }
            
            if (!transactionInQueue) {
                log.error("Cancellation transaction {} was not found in wallet pending queue after receivePending()", cancelTx.getTxId());
                return "Failed to queue cancellation transaction: Transaction was not added to wallet queue.";
            }
            
            // Try to broadcast immediately (will fail if locktime hasn't passed, but transaction is still queued)
            // Use Handler to post to main thread since AbstractWalletActivityViewModel requires main thread Looper
            String broadcastError = null;
            boolean broadcastSucceeded = false;
            
            // Post broadcast attempt to main thread to avoid Handler creation error
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            final java.util.concurrent.CountDownLatch broadcastLatch = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicReference<String> broadcastErrorRef = new java.util.concurrent.atomic.AtomicReference<String>();
            final java.util.concurrent.atomic.AtomicBoolean broadcastSucceededRef = new java.util.concurrent.atomic.AtomicBoolean(false);
            
            mainHandler.post(() -> {
                try {
                    // Create ViewModel on main thread to avoid Handler creation errors
                    AbstractWalletActivityViewModel viewModel;
                    try {
                        viewModel = new AbstractWalletActivityViewModel(app);
                    } catch (RuntimeException e) {
                        // If ViewModel creation fails (e.g., Handler error), log and skip immediate broadcast
                        // Transaction is already queued, so it will be broadcast automatically later
                        log.warn("Could not create ViewModel for immediate broadcast (transaction is queued): {}", e.getMessage());
                        if (locktime > currentTime) {
                            long timeRemaining = locktime - currentTime;
                            long days = timeRemaining / 86400;
                            long hours = (timeRemaining % 86400) / 3600;
                            long minutes = (timeRemaining % 3600) / 60;
                            String timeStr = "";
                            if (days > 0) timeStr += days + " day" + (days > 1 ? "s" : "") + " ";
                            if (hours > 0) timeStr += hours + " hour" + (hours > 1 ? "s" : "") + " ";
                            if (minutes > 0) timeStr += minutes + " minute" + (minutes > 1 ? "s" : "") + " ";
                            if (timeStr.isEmpty()) timeStr = "less than a minute ";
                            broadcastErrorRef.set("Transaction queued: Timelock not reached yet. " +
                                "The transaction will be broadcast automatically when the timelock is reached (in " + timeStr.trim() + ").");
                        } else {
                            broadcastErrorRef.set("Transaction queued. The transaction will be broadcast automatically.");
                        }
                        broadcastLatch.countDown();
                        return;
                    }
                    
                    com.google.common.util.concurrent.ListenableFuture<Transaction> broadcastFuture = viewModel.broadcastTransaction(cancelTx);
                    
                    // Wait a short time to see if broadcast fails immediately
                    try {
                        Transaction result = broadcastFuture.get(2, java.util.concurrent.TimeUnit.SECONDS);
                        log.info("Check cancellation transaction broadcast immediately: {}", result.getTxId());
                        broadcastSucceededRef.set(true);
                    } catch (java.util.concurrent.TimeoutException e) {
                        // Broadcast is still in progress - check if timelock hasn't passed
                        if (locktime > currentTime) {
                            // Timelock hasn't passed - this is expected, transaction is queued
                            long timeRemaining = locktime - currentTime;
                            long days = timeRemaining / 86400;
                            long hours = (timeRemaining % 86400) / 3600;
                            long minutes = (timeRemaining % 3600) / 60;
                            
                            String timeStr = "";
                            if (days > 0) timeStr += days + " day" + (days > 1 ? "s" : "") + " ";
                            if (hours > 0) timeStr += hours + " hour" + (hours > 1 ? "s" : "") + " ";
                            if (minutes > 0) timeStr += minutes + " minute" + (minutes > 1 ? "s" : "") + " ";
                            if (timeStr.isEmpty()) timeStr = "less than a minute ";
                            
                            broadcastErrorRef.set("Transaction queued: Timelock not reached yet. " +
                                "The transaction has been queued and will be broadcast automatically when the timelock is reached (in " + timeStr.trim() + ").");
                            log.info("Check cancellation transaction queued (timelock in {}): {}", timeStr.trim(), cancelTx.getTxId());
                        } else {
                            // Timelock has passed but broadcast is still in progress - this is fine
                            log.info("Check cancellation transaction broadcast in progress (locktime reached)");
                            broadcastSucceededRef.set(true); // Consider it successful if timelock passed
                        }
                    } catch (Exception e) {
                        // Broadcast failed - extract detailed error message
                        Throwable cause = e.getCause();
                        String errorMsg = e.getMessage();
                        if (cause != null) {
                            errorMsg = cause.getMessage();
                            if (errorMsg == null || errorMsg.isEmpty()) {
                                errorMsg = cause.getClass().getSimpleName();
                            }
                        }
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = e.getClass().getSimpleName();
                        }
                        
                        // Check if it's a timelock-related error
                        if (locktime > currentTime) {
                            long timeRemaining = locktime - currentTime;
                            long days = timeRemaining / 86400;
                            long hours = (timeRemaining % 86400) / 3600;
                            long minutes = (timeRemaining % 3600) / 60;
                            
                            String timeStr = "";
                            if (days > 0) timeStr += days + " day" + (days > 1 ? "s" : "") + " ";
                            if (hours > 0) timeStr += hours + " hour" + (hours > 1 ? "s" : "") + " ";
                            if (minutes > 0) timeStr += minutes + " minute" + (minutes > 1 ? "s" : "") + " ";
                            if (timeStr.isEmpty()) timeStr = "less than a minute ";
                            
                            broadcastErrorRef.set("Transaction rejected: Timelock not reached yet. " +
                                "The transaction has been queued and will be broadcast automatically when the timelock is reached (in " + timeStr.trim() + "). " +
                                "Peer rejection reason: " + errorMsg);
                        } else {
                            broadcastErrorRef.set("Transaction rejected by peers: " + errorMsg + 
                                ". The transaction has been queued and will be retried automatically.");
                        }
                        log.warn("Check cancellation transaction broadcast failed: {}", errorMsg, e);
                    }
                } catch (Exception e) {
                    log.warn("Error attempting to broadcast check cancellation transaction: {}", e.getMessage());
                    // Transaction is still queued, so this is not a fatal error
                    // But we should still show an error message
                    broadcastErrorRef.set("Error attempting to broadcast transaction: " + e.getMessage() + 
                        ". The transaction has been queued and will be retried automatically.");
                } finally {
                    broadcastLatch.countDown();
                }
            });
            
            // Wait for broadcast attempt to complete (with timeout)
            try {
                boolean completed = broadcastLatch.await(3, java.util.concurrent.TimeUnit.SECONDS);
                if (completed) {
                    broadcastError = broadcastErrorRef.get();
                    broadcastSucceeded = broadcastSucceededRef.get();
                } else {
                    // Timeout - assume it's still in progress
                    if (locktime > currentTime) {
                        broadcastError = "Transaction queued: Timelock not reached yet. The transaction will be broadcast automatically when ready.";
                    } else {
                        broadcastSucceeded = true; // Consider it successful if timelock passed
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for broadcast attempt");
                broadcastError = "Transaction queued. The transaction will be broadcast automatically when ready.";
            }
            
            // Update check status only if transaction is confirmed to be in wallet queue
            check.setStatus("canceled");
            try {
                if (db != null && db.isOpen()) {
                    db.checkDao().updateCheck(check);
                } else {
                    log.warn("Database not available for status update");
                }
            } catch (Exception e) {
                log.error("Error updating check status to canceled: {}", e.getMessage(), e);
            }
            
            if (broadcastSucceeded) {
                log.info("Check {} canceled successfully, funds returned to {}", check.getId(), returnAddress);
                // Broadcast succeeded immediately - return null (success)
                return null;
            } else {
                // Transaction is queued but not yet broadcast
                // If timelock hasn't passed, this is expected and should be considered success
                if (locktime > currentTime) {
                    log.info("Check {} cancellation transaction queued (will broadcast when ready), funds will be returned to {}", 
                        check.getId(), returnAddress);
                    // Transaction is queued and will broadcast when timelock is reached - this is success
                    return null;
                } else {
                    // Timelock has passed but broadcast failed - return error message
                    log.warn("Check {} cancellation transaction queued but broadcast failed (timelock reached)", check.getId());
                    return broadcastError != null ? broadcastError : "Transaction queued but broadcast failed. It will be retried automatically.";
                }
            }
                    
                } catch (Exception e) {
                    log.error("Error canceling check", e);
                    return "Error creating cancellation transaction: " + e.getMessage();
                }
            }
            
            private void shareCheck(Check check) {
                try {
                    Coin amount = Coin.valueOf(check.getAmount());
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    
                    StringBuilder shareText = new StringBuilder();
                    shareText.append("Dogecoin Check\n");
                    shareText.append("Check #").append(check.getId()).append("\n");
                    shareText.append("Pay to: ").append(check.getPayTo()).append("\n");
                    shareText.append("Amount: ").append(amount.toPlainString()).append(" DOGE\n");
                    shareText.append("Date: ").append(dateFormat.format(check.getDate())).append("\n");
                    
                    if (check.getExpirationDate() != null) {
                        shareText.append("Expiration Date: ").append(dateFormat.format(check.getExpirationDate())).append("\n");
                    }
                    
                    if (check.getMemo() != null && !check.getMemo().trim().isEmpty()) {
                        shareText.append("Memo: ").append(check.getMemo()).append("\n");
                    }
                    
                    shareText.append("Signature: ").append(check.getSignature()).append("\n");
                    shareText.append("Address: ").append(check.getAddress()).append("\n");
                    shareText.append("Status: ").append(check.getStatus()).append("\n");
                    
                    if (check.getTransactionHash() != null && !check.getTransactionHash().trim().isEmpty()) {
                        shareText.append("Transaction ID: ").append(check.getTransactionHash()).append("\n");
                    }
                    
                    // Include private key (WIF format) for manual sweeping
                    if (check.getDerivedKey() != null && !check.getDerivedKey().trim().isEmpty()) {
                        shareText.append("\nPrivate Key (WIF): ").append(check.getDerivedKey()).append("\n");
                        shareText.append("Note: This private key can be used to sweep the check funds manually.\n");
                    }
                    
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Dogecoin Check #" + check.getId());
                    
                    startActivity(Intent.createChooser(shareIntent, "Share Check"));
                } catch (Exception e) {
                    log.error("Error sharing check", e);
                    Toast.makeText(WriteCheckActivity.this, "Error sharing check: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}

