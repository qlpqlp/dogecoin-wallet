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

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.DialogFragment;

import de.schildbach.wallet.R;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.Check;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.util.ExcludedAddressHelper;
import de.schildbach.wallet.util.SecureMemory;
import de.schildbach.wallet.util.Qr;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.core.InsufficientMoneyException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog fragment for creating a new Dogecoin check (timelock transaction)
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class CreateCheckDialogFragment extends DialogFragment {
    private static final Logger log = LoggerFactory.getLogger(CreateCheckDialogFragment.class);
    
    private EditText editPayTo;
    private Button btnSelectDate;
    private Button btnSelectExpirationDate;
    private EditText editAmount;
    private EditText editMemo;
    private TextView textSignature;
    private Button btnCancel;
    private Button btnIssue;
    
    private Date selectedDate;
    private Date selectedExpirationDate;
    private OnCheckCreatedListener listener;
    
    public interface OnCheckCreatedListener {
        void onCheckCreated(Check check);
    }
    
    public void setOnCheckCreatedListener(OnCheckCreatedListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_create_check, null);
        
        editPayTo = view.findViewById(R.id.edit_pay_to);
        btnSelectDate = view.findViewById(R.id.btn_select_date);
        btnSelectExpirationDate = view.findViewById(R.id.btn_select_expiration_date);
        editAmount = view.findViewById(R.id.edit_amount);
        editMemo = view.findViewById(R.id.edit_memo);
        textSignature = view.findViewById(R.id.text_signature);
        btnCancel = view.findViewById(R.id.btn_cancel);
        btnIssue = view.findViewById(R.id.btn_issue);
        
        // Set signature from configuration
        Configuration config = getWalletApplication().getConfiguration();
        String ownName = config.getOwnName();
        textSignature.setText(ownName != null && !ownName.trim().isEmpty() ? ownName : getString(R.string.write_check_anonymous));
        
        // Set default date to tomorrow
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        selectedDate = calendar.getTime();
        updateDateButton();
        
        // Set default expiration date to 30 days from now
        Calendar expirationCalendar = Calendar.getInstance();
        expirationCalendar.add(Calendar.DAY_OF_MONTH, 30);
        selectedExpirationDate = expirationCalendar.getTime();
        updateExpirationDateButton();
        
        btnSelectDate.setOnClickListener(v -> showDatePicker());
        btnSelectExpirationDate.setOnClickListener(v -> showExpirationDatePicker());
        btnCancel.setOnClickListener(v -> dismiss());
        btnIssue.setOnClickListener(v -> issueCheck());
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireActivity());
        builder.setView(view);
        builder.setTitle(R.string.write_check_create_new);
        
        return builder.create();
    }
    
    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (selectedDate != null) {
            calendar.setTime(selectedDate);
        } else {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireActivity(),
            (view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                selectedDate = selected.getTime();
                updateDateButton();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        
        // Set minimum date to tomorrow
        Calendar minDate = Calendar.getInstance();
        minDate.add(Calendar.DAY_OF_MONTH, 1);
        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
        
        datePickerDialog.show();
    }
    
    private void updateDateButton() {
        if (selectedDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            btnSelectDate.setText(dateFormat.format(selectedDate));
        }
    }
    
    private void showExpirationDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (selectedExpirationDate != null) {
            calendar.setTime(selectedExpirationDate);
        } else {
            calendar.add(Calendar.DAY_OF_MONTH, 30);
        }
        
        // Minimum expiration date must be after the locktime date
        Calendar minDate = Calendar.getInstance();
        if (selectedDate != null) {
            minDate.setTime(selectedDate);
            minDate.add(Calendar.DAY_OF_MONTH, 1); // At least 1 day after locktime
        } else {
            minDate.add(Calendar.DAY_OF_MONTH, 2); // At least 2 days from now
        }
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireActivity(),
            (view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                selectedExpirationDate = selected.getTime();
                updateExpirationDateButton();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        
        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
        datePickerDialog.show();
    }
    
    private void updateExpirationDateButton() {
        if (selectedExpirationDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            btnSelectExpirationDate.setText(dateFormat.format(selectedExpirationDate));
        }
    }
    
    private void issueCheck() {
        String payTo = editPayTo.getText().toString().trim();
        if (TextUtils.isEmpty(payTo)) {
            editPayTo.setError(getString(R.string.write_check_invalid_pay_to));
            return;
        }
        
        if (selectedDate == null || selectedDate.before(new Date())) {
            Toast.makeText(requireActivity(), R.string.write_check_invalid_date, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedExpirationDate == null || selectedExpirationDate.before(selectedDate)) {
            Toast.makeText(requireActivity(), R.string.write_check_invalid_expiration_date, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String amountStr = editAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            editAmount.setError(getString(R.string.write_check_invalid_amount));
            return;
        }
        
        double amountDouble;
        try {
            amountDouble = Double.parseDouble(amountStr);
            if (amountDouble <= 0) {
                editAmount.setError(getString(R.string.write_check_invalid_amount));
                return;
            }
        } catch (NumberFormatException e) {
            editAmount.setError(getString(R.string.write_check_invalid_amount));
            return;
        }
        
        // Convert DOGE to smallest units (1 DOGE = 100,000,000 smallest units)
        Coin amount = Coin.parseCoin(String.valueOf(amountDouble));
        String memo = editMemo.getText().toString().trim();
        String signature = textSignature.getText().toString();
        
        // Show loading
        btnIssue.setEnabled(false);
        btnIssue.setText(R.string.write_check_creating);
        
        // Get context and activity before starting background thread
        final Context context = requireContext();
        final android.app.Activity activity = requireActivity();
        
        // Create check on background thread
        new Thread(() -> {
            try {
                Check check = createCheck(payTo, selectedDate, selectedExpirationDate, amount.value, memo, signature, context);
                if (check != null) {
                    activity.runOnUiThread(() -> {
                        if (listener != null) {
                            listener.onCheckCreated(check);
                        }
                        dismiss();
                    });
                } else {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(context, R.string.write_check_error, Toast.LENGTH_SHORT).show();
                        btnIssue.setEnabled(true);
                        btnIssue.setText(R.string.write_check_issue);
                    });
                }
            } catch (Exception e) {
                log.error("Error creating check", e);
                activity.runOnUiThread(() -> {
                    Toast.makeText(context, R.string.write_check_error, Toast.LENGTH_SHORT).show();
                    btnIssue.setEnabled(true);
                    btnIssue.setText(R.string.write_check_issue);
                });
            }
        }).start();
    }
    
    @WorkerThread
    private Check createCheck(String payTo, Date date, Date expirationDate, long amount, String memo, String signature, Context context) {
        try {
            // Initialize BitcoinJ context if not already set
            try {
                if (org.bitcoinj.core.Context.get() == null) {
                    log.info("Initializing BitcoinJ context...");
                    org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
                    log.info("BitcoinJ context initialized successfully");
                } else {
                    log.info("BitcoinJ context already initialized");
                }
            } catch (Exception e) {
                log.error("Failed to initialize BitcoinJ context", e);
                // Try to create a new context using the network parameters
                try {
                    org.bitcoinj.core.Context bitcoinjContext = new org.bitcoinj.core.Context(Constants.NETWORK_PARAMETERS);
                    org.bitcoinj.core.Context.propagate(bitcoinjContext);
                    log.info("Created new BitcoinJ context with network parameters");
                } catch (Exception e2) {
                    log.error("Failed to create new BitcoinJ context", e2);
                    return null;
                }
            }
            
            WalletApplication app = (WalletApplication) context.getApplicationContext();
            Wallet wallet = app.getWallet();
            
            if (wallet == null) {
                log.error("Wallet not available");
                return null;
            }
            
            // Check if wallet is encrypted
            if (wallet.isEncrypted()) {
                log.error("Cannot create check - wallet is encrypted. User must decrypt wallet first.");
                return null;
            }
            
            // Generate derived key for the check
            DeterministicKeyChain keyChain = wallet.getActiveKeyChain();
            Address regularAddress = wallet.freshReceiveAddress();
            ECKey key = wallet.findKeyFromAddress(regularAddress);
            
            // Create CLTV (CheckLockTimeVerify) script address
            // Script: <locktime> OP_CHECKLOCKTIMEVERIFY OP_DROP <pubkey> OP_CHECKSIG
            // This ensures funds can only be spent after the locktime
            long lockTime = date.getTime() / 1000; // Unix timestamp in seconds
            
            // Build CLTV script: locktime OP_CHECKLOCKTIMEVERIFY OP_DROP pubkey OP_CHECKSIG
            Script cltvScript = new ScriptBuilder()
                .number(lockTime)  // Push locktime
                .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)  // Verify transaction locktime >= script locktime
                .op(ScriptOpCodes.OP_DROP)  // Drop locktime from stack
                .data(key.getPubKey())  // Push public key
                .op(ScriptOpCodes.OP_CHECKSIG)  // Verify signature
                .build();
            
            // Create P2SH (Pay-to-Script-Hash) address from the CLTV script
            // P2SH uses RIPEMD160(SHA256(script)) for the address hash
            // This is the address that will receive the check funds
            byte[] scriptHash = org.bitcoinj.core.Utils.sha256hash160(cltvScript.getProgram());
            Address checkAddress = LegacyAddress.fromScriptHash(Constants.NETWORK_PARAMETERS, scriptHash);
            
            // Add the P2SH script to wallet's watched scripts so it tracks transactions
            // This allows the wallet to monitor the check address in mempool and headers
            try {
                Script p2shOutputScript = ScriptBuilder.createP2SHOutputScript(scriptHash);
                // Use reflection to call addWatchedScript if available (BitcoinJ method)
                try {
                    java.lang.reflect.Method addWatchedScriptMethod = wallet.getClass().getMethod("addWatchedScript", Script.class);
                    addWatchedScriptMethod.invoke(wallet, p2shOutputScript);
                    log.info("Added P2SH script to wallet watched scripts: {}", checkAddress);
                } catch (NoSuchMethodException e) {
                    // Fallback: try addWatchedAddress
                    try {
                        java.lang.reflect.Method addWatchedAddressMethod = wallet.getClass().getMethod("addWatchedAddress", Address.class);
                        addWatchedAddressMethod.invoke(wallet, checkAddress);
                        log.info("Added check address to wallet watched addresses: {}", checkAddress);
                    } catch (NoSuchMethodException e2) {
                        log.warn("Could not add check address to wallet watched list - wallet may not track this address");
                    }
                }
            } catch (Exception e) {
                log.warn("Error adding check address to wallet watched list: {}", e.getMessage());
            }
            
            log.info("Created CLTV script address: {} (locktime: {}, date: {})", 
                checkAddress.toString(), lockTime, date);
            
            // Convert private key to WIF format for sweep wallet compatibility
            // WIF format is what the paper wallet converter (sweep wallet) expects
            // WIF format can be parsed by DumpedPrivateKey.fromBase58()
            String derivedKey = null;
            try {
                // Get private key bytes
                byte[] privateKeyBytes = key.getPrivKeyBytes();
                if (privateKeyBytes == null) {
                    throw new IllegalStateException("Private key bytes are null");
                }
                
                // Manually create WIF format:
                // 1. Get private key version byte (0x9E for Dogecoin mainnet)
                // 2. Append 32-byte private key
                // 3. If compressed, append 0x01
                // 4. Calculate checksum (first 4 bytes of double SHA256)
                // 5. Base58 encode everything
                int versionInt = Constants.NETWORK_PARAMETERS.getDumpedPrivateKeyHeader();
                byte version = (byte) versionInt;
                byte[] versionedKeyBytes = new byte[1 + privateKeyBytes.length + (key.isCompressed() ? 1 : 0)];
                versionedKeyBytes[0] = version;
                System.arraycopy(privateKeyBytes, 0, versionedKeyBytes, 1, privateKeyBytes.length);
                if (key.isCompressed()) {
                    versionedKeyBytes[versionedKeyBytes.length - 1] = 0x01;
                }
                
                // Calculate checksum (first 4 bytes of double SHA256)
                byte[] hash = Sha256Hash.hashTwice(versionedKeyBytes);
                byte[] checksum = new byte[4];
                System.arraycopy(hash, 0, checksum, 0, 4);
                
                // Combine versioned key + checksum
                byte[] wifBytes = new byte[versionedKeyBytes.length + 4];
                System.arraycopy(versionedKeyBytes, 0, wifBytes, 0, versionedKeyBytes.length);
                System.arraycopy(checksum, 0, wifBytes, versionedKeyBytes.length, 4);
                
                // Base58 encode - use reflection to call Base58.encode() static method
                // Base58 in bitcoinj is a utility class with static encode/decode methods
                try {
                    java.lang.reflect.Method encodeMethod = org.bitcoinj.core.Base58.class.getMethod("encode", byte[].class);
                    derivedKey = (String) encodeMethod.invoke(null, wifBytes);
                    log.info("Base58 encoding successful via reflection");
                } catch (Exception reflectEx) {
                    log.error("Base58 encoding via reflection failed", reflectEx);
                    // Last resort: try to create a temporary DumpedPrivateKey by encoding/decoding
                    // This is a workaround - we'll create a test key and extract its format
                    throw new IllegalStateException("Cannot encode WIF format - Base58.encode() not accessible", reflectEx);
                }
                
                log.info("Generated WIF key: length={}, preview={}...", 
                    derivedKey.length(), derivedKey.substring(0, Math.min(10, derivedKey.length())));
                
                // Verify it can be parsed back (this ensures it's valid WIF format)
                try {
                    DumpedPrivateKey testKey = DumpedPrivateKey.fromBase58(Constants.NETWORK_PARAMETERS, derivedKey);
                    ECKey testEcKey = testKey.getKey();
                    // Verify the key matches
                    if (java.util.Arrays.equals(testEcKey.getPrivKeyBytes(), privateKeyBytes)) {
                        log.info("Successfully created and verified WIF format key (starts with: {})", 
                            derivedKey.substring(0, Math.min(5, derivedKey.length())));
                    } else {
                        log.warn("WIF key created but private key bytes don't match");
                    }
                } catch (Exception verifyEx) {
                    log.error("WIF format verification failed, key might be invalid", verifyEx);
                    throw new IllegalStateException("Generated WIF key cannot be parsed back", verifyEx);
                }
            } catch (Exception e) {
                log.error("Error converting key to WIF format: {}", e.getMessage(), e);
                // Fallback to BIP32 format if WIF conversion fails
            if (key instanceof DeterministicKey) {
                DeterministicKey detKey = (DeterministicKey) key;
                derivedKey = detKey.serializePrivB58(Constants.NETWORK_PARAMETERS);
                    log.warn("Fell back to BIP32 format (not compatible with sweep wallet)");
            } else {
                derivedKey = "xprv" + key.getPrivateKeyAsHex();
                    log.warn("Fell back to hex format (not compatible with sweep wallet)");
                }
            }
            
            // Securely clear the private key bytes from memory
            try {
                byte[] privateKeyBytes = key.getPrivKeyBytes();
                if (privateKeyBytes != null) {
                    SecureMemory.clear(privateKeyBytes);
                }
            } catch (Exception e) {
                // Ignore
            }
            
            // Create check record
            Check check = new Check(payTo, date, expirationDate, amount, memo, signature, checkAddress.toString(), derivedKey);
            
            // Save to database
            AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
            if (database == null || !database.isOpen()) {
                log.error("Database is null or closed, cannot save check");
                throw new RuntimeException("Database not available");
            }
            
            long checkId;
            try {
                checkId = database.checkDao().insertCheck(check);
                check.setId(checkId);
            } catch (android.database.sqlite.SQLiteException e) {
                log.error("Database error while inserting check: {}", e.getMessage(), e);
                throw new RuntimeException("Database error: " + e.getMessage(), e);
            } catch (IllegalStateException e) {
                log.error("Database access error while inserting check: {}", e.getMessage(), e);
                throw new RuntimeException("Database access error: " + e.getMessage(), e);
            }
            
            // Add address to address book
            try {
                database.addressBookDao().insertOrUpdate(
                    new de.schildbach.wallet.addressbook.AddressBookEntry(
                        checkAddress.toString(), 
                        "Check: " + payTo
                    )
                );
            } catch (Exception e) {
                log.warn("Error adding check address to address book: {}", e.getMessage());
                // Don't fail check creation if address book update fails
            }
            
            // Exclude address from spending
            ExcludedAddressHelper.excludeAddress(checkAddress.toString(), "Check: " + payTo);
            
            // Create timelock transaction (convert long amount to Coin)
            // Note: The CLTV script already enforces the timelock at the address level
            // We still set transaction locktime for additional security
            Coin amountCoin = Coin.valueOf(amount);
            createTimelockTransaction(wallet, checkAddress, amountCoin, date, check, context, cltvScript, scriptHash);
            
            return check;
        } catch (ECKey.KeyIsEncryptedException e) {
            log.error("Cannot create check - wallet is encrypted. User must decrypt wallet first.", e);
            return null;
        } catch (RuntimeException e) {
            // This catches RuntimeException from createTimelockTransaction (which wraps InsufficientMoneyException and other exceptions)
            log.error("Error creating check: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Error creating check", e);
            return null;
        }
    }
    
    @WorkerThread
    private void createTimelockTransaction(Wallet wallet, Address checkAddress, Coin amount, Date date, Check check, Context context, Script cltvScript, byte[] scriptHash) {
        try {
            // Ensure BitcoinJ context is set
            org.bitcoinj.core.Context.propagate(Constants.CONTEXT);
            
            // Create transaction to send funds to the CLTV script address
            // Note: The CLTV script address can RECEIVE funds immediately
            // The timelock only applies when SPENDING from that address
            // So we don't need to set transaction locktime - we can broadcast immediately
            Transaction transaction = new Transaction(Constants.NETWORK_PARAMETERS);
            
            // Add output using P2SH script (not the CLTV script directly)
            // For P2SH, we need to create a script that pays to the script hash
            // The CLTV script is the redeem script that will be used when spending
            Script p2shScript = ScriptBuilder.createP2SHOutputScript(scriptHash);
            transaction.addOutput(amount, p2shScript);
            
            log.info("Created transaction sending to CLTV script address (script locktime: {})", 
                date.getTime() / 1000);
            
            // Create send request
            SendRequest sendRequest = SendRequest.forTx(transaction);
            sendRequest.feePerKb = Coin.valueOf(1000000); // 1 DOGE per KB
            sendRequest.memo = "Check: " + check.getPayTo();
            
            // Complete and sign transaction
            wallet.completeTx(sendRequest);
            
            // Save transaction hash
            String txHash = transaction.getTxId().toString();
            check.setTransactionHash(txHash);
            
            // Update check in database
            AddressBookDatabase database = AddressBookDatabase.getDatabase(context);
            if (database == null || !database.isOpen()) {
                log.error("Database is null or closed, cannot update check");
                return;
            }
            
            try {
                database.checkDao().updateCheck(check);
            } catch (android.database.sqlite.SQLiteException e) {
                log.error("Database error while updating check: {}", e.getMessage(), e);
            } catch (IllegalStateException e) {
                log.error("Database access error while updating check: {}", e.getMessage(), e);
            }
            
            // Store transaction in wallet first (so it's tracked)
            wallet.receivePending(transaction, null);
            log.info("Transaction stored in wallet: {} (isRelevant: {})", txHash, wallet.isTransactionRelevant(transaction));
            
            // Broadcast transaction immediately
            // The CLTV script address can receive funds at any time
            // The timelock only applies when spending FROM the address, not when sending TO it
            // Must create ViewModel on main thread since it creates a Handler
            WalletApplication app = (WalletApplication) context.getApplicationContext();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    AbstractWalletActivityViewModel viewModel = new AbstractWalletActivityViewModel(app);
                    log.info("Attempting to broadcast transaction: {} (inputs: {}, outputs: {})", 
                        txHash, transaction.getInputs().size(), transaction.getOutputs().size());
                    
                    // Always broadcast check transactions - they send funds to P2SH addresses
                    // The wallet may not recognize them as relevant until broadcast
                    boolean isRelevant = wallet.isTransactionRelevant(transaction);
                    log.info("Transaction relevance check: {} (relevant: {})", txHash, isRelevant);
                    
                    // Broadcast regardless of relevance - check transactions need to be broadcast
                    // to send funds to the P2SH check address
                    ListenableFuture<Transaction> future = viewModel.broadcastTransaction(transaction);
                    future.addListener(() -> {
                        try {
                            Transaction result = future.get();
                            log.info("Transaction broadcast completed successfully: {}", txHash);
                        } catch (Exception e) {
                            log.error("Transaction broadcast future failed: {}", txHash, e);
                        }
                    }, MoreExecutors.directExecutor());
                    log.info("Transaction broadcast initiated: {} (sending to CLTV address, script locktime: {})", 
                        txHash, date.getTime() / 1000);
                } catch (Exception e) {
                    log.error("Failed to broadcast transaction: {}", txHash, e);
                    // Transaction is already stored in wallet, so it will be visible
                }
            });
            
            log.info("Timelock transaction created: {}", txHash);
        } catch (ECKey.KeyIsEncryptedException e) {
            log.error("Cannot create timelock transaction - wallet is encrypted", e);
            throw new RuntimeException("Wallet is encrypted. Please decrypt wallet first.", e);
        } catch (InsufficientMoneyException e) {
            log.error("Insufficient funds for timelock transaction", e);
            throw new RuntimeException("Insufficient funds to create check", e);
        } catch (Exception e) {
            log.error("Error creating timelock transaction", e);
            throw new RuntimeException("Failed to create timelock transaction: " + e.getMessage(), e);
        }
    }
    
    private WalletApplication getWalletApplication() {
        return (WalletApplication) requireActivity().getApplication();
    }
}

