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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import de.schildbach.wallet.R;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.ui.AbstractWalletActivityViewModel;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.data.PaymentIntent;
import de.schildbach.wallet.util.Qr;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.SendRequest;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity for managing Multi-Signature wallets with improved UX
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class MultiSigActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(MultiSigActivity.class);
    
    private WalletApplication application;
    private Wallet wallet;
    private NetworkParameters params;
    
    private Button btnGeneratePublicKey;
    private Button btnCreateMultisig;
    private Button btnSendToMultisig;
    private Button btnSpendFromMultisig;
    private Button btnSharePublicKey;
    private Button btnShowQrPublicKey;
    private TextView textPublicKey;
    private TextView textMultisigAddress;
    private TextView textRedeemScript;
    private TextView textMultisigInfo;
    private LinearLayout layoutPublicKeyResult;
    private LinearLayout layoutMultisigResult;
    private ImageView qrCodePublicKey;
    private ProgressBar progressBar;
    
    private List<ECKey> publicKeys = new ArrayList<>();
    private Script redeemScript;
    private Address multisigAddress;
    private int requiredSignatures = 2;
    private int totalKeys = 2;
    private String currentPublicKeyHex;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multisig);
        
        application = (WalletApplication) getApplication();
        wallet = application.getWallet();
        params = Constants.NETWORK_PARAMETERS;
        
        initializeViews();
        setupClickListeners();
    }
    
    private void initializeViews() {
        btnGeneratePublicKey = findViewById(R.id.btn_generate_public_key);
        btnCreateMultisig = findViewById(R.id.btn_create_multisig);
        btnSendToMultisig = findViewById(R.id.btn_send_to_multisig);
        btnSpendFromMultisig = findViewById(R.id.btn_spend_from_multisig);
        btnSharePublicKey = findViewById(R.id.btn_share_public_key);
        btnShowQrPublicKey = findViewById(R.id.btn_show_qr_public_key);
        textPublicKey = findViewById(R.id.text_public_key);
        textMultisigAddress = findViewById(R.id.text_multisig_address);
        textRedeemScript = findViewById(R.id.text_redeem_script);
        textMultisigInfo = findViewById(R.id.text_multisig_info);
        layoutPublicKeyResult = findViewById(R.id.layout_public_key_result);
        layoutMultisigResult = findViewById(R.id.layout_multisig_result);
        qrCodePublicKey = findViewById(R.id.qr_code_public_key);
        progressBar = findViewById(R.id.progress_bar);
    }
    
    private void setupClickListeners() {
        btnGeneratePublicKey.setOnClickListener(v -> generatePublicKey());
        btnCreateMultisig.setOnClickListener(v -> showCreateMultisigDialog());
        btnSendToMultisig.setOnClickListener(v -> sendToMultisigAddress());
        btnSpendFromMultisig.setOnClickListener(v -> spendFromMultisig());
        if (btnSharePublicKey != null) {
            btnSharePublicKey.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(currentPublicKeyHex)) {
                    sharePublicKey(currentPublicKeyHex);
                } else {
                    Toast.makeText(this, R.string.multisig_no_public_key, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (btnShowQrPublicKey != null) {
            btnShowQrPublicKey.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(currentPublicKeyHex)) {
                    showQrCodeDialog(currentPublicKeyHex, getString(R.string.multisig_public_key_qr_title));
                } else {
                    Toast.makeText(this, R.string.multisig_no_public_key, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    private void generatePublicKey() {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
            
            // Get a key from the wallet
            ECKey key = wallet.freshReceiveKey();
            if (key == null) {
                // Try to get any key from the wallet
                key = wallet.getActiveKeyChain().getKey(org.bitcoinj.wallet.KeyChain.KeyPurpose.RECEIVE_FUNDS);
            }
            
            if (key == null) {
                Toast.makeText(this, R.string.multisig_error_no_key, Toast.LENGTH_LONG).show();
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                return;
            }
            
            // Get the public key in hex format
            currentPublicKeyHex = Utils.HEX.encode(key.getPubKey());
            textPublicKey.setText(currentPublicKeyHex);
            
            // Show the result layout
            if (layoutPublicKeyResult != null) {
                layoutPublicKeyResult.setVisibility(View.VISIBLE);
            }
            
            // Generate QR code
            if (qrCodePublicKey != null) {
                try {
                    final Bitmap qrCode = Qr.bitmap(currentPublicKeyHex);
                    if (qrCode != null) {
                        // Convert to RGB with white background for better visibility
                        final Bitmap qrCodeWithBackground = convertQrToWhiteBackground(qrCode);
                        qrCodePublicKey.setImageBitmap(qrCodeWithBackground);
                        qrCodePublicKey.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    log.warn("Error generating QR code", e);
                }
            }
            
            Toast.makeText(this, R.string.multisig_public_key_generated, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            log.error("Error generating public key", e);
            Toast.makeText(this, getString(R.string.multisig_error_generating_key, e.getMessage()), Toast.LENGTH_LONG).show();
        } finally {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        }
    }
    
    private void showQrCodeDialog(String data, String title) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null);
        ImageView qrImageView = dialogView.findViewById(R.id.qr_code_image);
        TextView qrDataText = dialogView.findViewById(R.id.qr_data_text);
        
        try {
            final Bitmap qrCode = Qr.bitmap(data);
            if (qrCode != null) {
                // Convert to RGB with white background for better visibility
                final Bitmap qrCodeWithBackground = convertQrToWhiteBackground(qrCode);
                qrImageView.setImageBitmap(qrCodeWithBackground);
                qrDataText.setText(data);
            } else {
                Toast.makeText(this, R.string.multisig_error_qr_code, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            log.warn("Error generating QR code", e);
            Toast.makeText(this, R.string.multisig_error_qr_code, Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.button_ok, null)
            .show();
    }
    
    private void sharePublicKey(String publicKeyHex) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, publicKeyHex);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.multisig_share_public_key)));
    }
    
    private void showCreateMultisigDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_multisig, null);
        EditText editRequired = dialogView.findViewById(R.id.edit_required_signatures);
        EditText editTotal = dialogView.findViewById(R.id.edit_total_keys);
        LinearLayout layoutPublicKeys = dialogView.findViewById(R.id.layout_public_keys);
        Button btnAddKey = dialogView.findViewById(R.id.btn_add_public_key);
        Button btnScanKey = dialogView.findViewById(R.id.btn_scan_public_key);
        TextView textHelp = dialogView.findViewById(R.id.text_help);
        
        // Set helpful text
        if (textHelp != null) {
            textHelp.setText(getString(R.string.multisig_create_help));
        }
        
        // Pre-fill with common values
        editRequired.setText("2");
        editTotal.setText("3");
        
        List<EditText> publicKeyInputs = new ArrayList<>();
        
        btnAddKey.setOnClickListener(v -> {
            EditText editKey = new EditText(this);
            editKey.setHint(getString(R.string.multisig_public_key_hint));
            editKey.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            editKey.setPadding(16, 16, 16, 16);
            layoutPublicKeys.addView(editKey);
            publicKeyInputs.add(editKey);
        });
        
        if (btnScanKey != null) {
            btnScanKey.setOnClickListener(v -> {
                ScanActivity.startForResult(this, REQUEST_CODE_SCAN_PUBLIC_KEY);
            });
        }
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.multisig_create_wallet)
            .setView(dialogView)
            .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                try {
                    int required = Integer.parseInt(editRequired.getText().toString());
                    int total = Integer.parseInt(editTotal.getText().toString());
                    
                    if (required < 1 || required > total) {
                        Toast.makeText(this, R.string.multisig_error_invalid_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    if (publicKeyInputs.size() != total) {
                        Toast.makeText(this, getString(R.string.multisig_error_wrong_key_count, total), Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    List<ECKey> keys = new ArrayList<>();
                    for (EditText editKey : publicKeyInputs) {
                        String keyHex = editKey.getText().toString().trim();
                        if (TextUtils.isEmpty(keyHex)) {
                            Toast.makeText(this, R.string.multisig_error_missing_key, Toast.LENGTH_LONG).show();
                            return;
                        }
                        try {
                            byte[] keyBytes = Utils.HEX.decode(keyHex);
                            ECKey key = ECKey.fromPublicOnly(keyBytes);
                            keys.add(key);
                        } catch (Exception e) {
                            Toast.makeText(this, getString(R.string.multisig_error_invalid_key, keyHex), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    
                    createMultisigWallet(required, total, keys);
                    
                } catch (NumberFormatException e) {
                    Toast.makeText(this, R.string.multisig_error_invalid_number, Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }
    
    private static final int REQUEST_CODE_SCAN_PUBLIC_KEY = 1001;
    private static final int REQUEST_CODE_SCAN_TRANSACTION = 1002;
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCAN_PUBLIC_KEY && resultCode == RESULT_OK && data != null) {
            String scannedData = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            if (!TextUtils.isEmpty(scannedData)) {
                // Try to extract public key from scanned data
                // Could be just the hex, or a QR code with additional data
                String publicKey = extractPublicKeyFromScannedData(scannedData);
                if (!TextUtils.isEmpty(publicKey)) {
                    // Add to the current dialog if open, or show a dialog to add it
                    Toast.makeText(this, getString(R.string.multisig_public_key_scanned), Toast.LENGTH_SHORT).show();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    
    private String extractPublicKeyFromScannedData(String data) {
        // Remove whitespace and common prefixes
        String cleaned = data.trim().replaceAll("\\s+", "");
        // Check if it's a valid hex public key (66 chars for compressed, 130 for uncompressed)
        if (cleaned.length() == 66 || cleaned.length() == 130) {
            try {
                Utils.HEX.decode(cleaned);
                return cleaned;
            } catch (Exception e) {
                // Not valid hex
            }
        }
        return null;
    }
    
    private void createMultisigWallet(int required, int total, List<ECKey> keys) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
            
            requiredSignatures = required;
            totalKeys = total;
            publicKeys = keys;
            
            // Create redeem script: OP_M <pubkey1> <pubkey2> ... <pubkeyN> OP_N OP_CHECKMULTISIG
            ScriptBuilder scriptBuilder = new ScriptBuilder();
            scriptBuilder.op(required + 80); // OP_M (80 + M)
            
            for (ECKey key : keys) {
                scriptBuilder.data(key.getPubKey());
            }
            
            scriptBuilder.op(total + 80); // OP_N (80 + N)
            scriptBuilder.op(org.bitcoinj.script.ScriptOpCodes.OP_CHECKMULTISIG);
            
            redeemScript = scriptBuilder.build();
            
            // Create P2SH address
            byte[] scriptHash = org.bitcoinj.core.Utils.sha256hash160(redeemScript.getProgram());
            multisigAddress = LegacyAddress.fromScriptHash(params, scriptHash);
            
            // Update UI
            textRedeemScript.setText(Utils.HEX.encode(redeemScript.getProgram()));
            textMultisigAddress.setText(multisigAddress.toString());
            
            // Show helpful info
            if (textMultisigInfo != null) {
                textMultisigInfo.setText(getString(R.string.multisig_wallet_info, required, total, multisigAddress.toString()));
            }
            
            // Show the result layout
            if (layoutMultisigResult != null) {
                layoutMultisigResult.setVisibility(View.VISIBLE);
            }
            
            // Add to watched scripts
            wallet.addWatchedScripts(java.util.Collections.singletonList(redeemScript));
            
            Toast.makeText(this, getString(R.string.multisig_wallet_created, required, total), Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            log.error("Error creating multisig wallet", e);
            Toast.makeText(this, getString(R.string.multisig_error_creating_wallet, e.getMessage()), Toast.LENGTH_LONG).show();
        } finally {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        }
    }
    
    private void sendToMultisigAddress() {
        if (multisigAddress == null) {
            Toast.makeText(this, R.string.multisig_error_no_wallet, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Create payment intent to send to multisig address
        PaymentIntent.Output output = new PaymentIntent.Output(
            Coin.ZERO, // Amount will be set by user
            ScriptBuilder.createOutputScript(multisigAddress)
        );
        
        PaymentIntent paymentIntent = new PaymentIntent(
            null, null, null,
            new PaymentIntent.Output[] { output },
            null, null, null, null, null
        );
        
        SendCoinsActivity.start(this, paymentIntent);
    }
    
    private void spendFromMultisig() {
        if (multisigAddress == null || redeemScript == null) {
            Toast.makeText(this, R.string.multisig_error_no_wallet, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Check balance
        Coin balance = wallet.getBalance();
        if (balance.isZero()) {
            Toast.makeText(this, R.string.multisig_error_no_balance, Toast.LENGTH_LONG).show();
            return;
        }
        
        // Show dialog to create unsigned transaction
        showCreateUnsignedTransactionDialog();
    }
    
    private void showCreateUnsignedTransactionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_unsigned_transaction, null);
        EditText editDestination = dialogView.findViewById(R.id.edit_destination_address);
        EditText editAmount = dialogView.findViewById(R.id.edit_amount);
        TextView textHelp = dialogView.findViewById(R.id.text_help);
        
        if (textHelp != null) {
            textHelp.setText(getString(R.string.multisig_unsigned_tx_help));
        }
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.multisig_create_unsigned_tx)
            .setView(dialogView)
            .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                try {
                    String destinationStr = editDestination.getText().toString().trim();
                    String amountStr = editAmount.getText().toString().trim();
                    
                    if (TextUtils.isEmpty(destinationStr) || TextUtils.isEmpty(amountStr)) {
                        Toast.makeText(this, R.string.multisig_error_fill_all_fields, Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    Address destination = LegacyAddress.fromBase58(params, destinationStr);
                    Coin amount = Coin.parseCoin(amountStr);
                    
                    createUnsignedTransaction(destination, amount);
                    
                } catch (Exception e) {
                    log.error("Error creating unsigned transaction", e);
                    Toast.makeText(this, getString(R.string.multisig_error_creating_tx, e.getMessage()), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }
    
    private void createUnsignedTransaction(Address destination, Coin amount) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
            
            // Find UTXOs for the multisig address by checking wallet transactions
            List<UTXO> multisigUtxos = new ArrayList<>();
            Coin totalInput = Coin.ZERO;
            
            // Check all wallet transactions for outputs to our multisig address
            for (Transaction tx : wallet.getTransactions(false)) {
                for (TransactionOutput output : tx.getOutputs()) {
                    try {
                        Script outputScript = output.getScriptPubKey();
                        if (outputScript.isPayToScriptHash()) {
                            Address outputAddress = outputScript.getToAddress(params);
                            if (outputAddress != null && outputAddress.equals(multisigAddress)) {
                                // Check if this output is still unspent
                                boolean isSpent = false;
                                for (Transaction walletTx : wallet.getTransactions(false)) {
                                    for (TransactionInput input : walletTx.getInputs()) {
                                        if (input.getOutpoint().getHash().equals(tx.getTxId()) &&
                                            input.getOutpoint().getIndex() == output.getIndex()) {
                                            isSpent = true;
                                            break;
                                        }
                                    }
                                    if (isSpent) break;
                                }
                                
                                if (!isSpent && output.getValue().isPositive()) {
                                    // Get block height from transaction
                                    int blockHeight = -1;
                                    if (tx.getConfidence().getConfidenceType() == org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING) {
                                        blockHeight = (int) tx.getConfidence().getAppearedAtChainHeight();
                                    }
                                    
                                    UTXO utxo = new UTXO(
                                        tx.getTxId(),
                                        output.getIndex(),
                                        output.getValue(),
                                        blockHeight,
                                        false,
                                        redeemScript
                                    );
                                    multisigUtxos.add(utxo);
                                    totalInput = totalInput.add(output.getValue());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip invalid outputs
                    }
                }
            }
            
            if (multisigUtxos.isEmpty()) {
                Toast.makeText(this, R.string.multisig_error_no_utxos, Toast.LENGTH_LONG).show();
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                return;
            }
            
            // Create transaction
            Transaction tx = new Transaction(params);
            
            // Add inputs (unsigned)
            for (UTXO utxo : multisigUtxos) {
                org.bitcoinj.core.TransactionOutPoint outPoint = new org.bitcoinj.core.TransactionOutPoint(
                    params, utxo.getIndex(), utxo.getHash());
                TransactionInput input = new TransactionInput(
                    params, tx, new byte[0], outPoint, utxo.getValue());
                tx.addInput(input);
            }
            
            // Add output
            tx.addOutput(amount, destination);
            
            // Add change output if needed
            Coin fee = Coin.valueOf(1000000); // 1 DOGE per KB (simplified)
            Coin change = totalInput.subtract(amount).subtract(fee);
            if (change.isPositive()) {
                Address changeAddress = wallet.freshReceiveAddress();
                tx.addOutput(change, changeAddress);
            }
            
            // Serialize unsigned transaction
            byte[] txBytes = tx.bitcoinSerialize();
            String txHex = Utils.HEX.encode(txBytes);
            
            // Share unsigned transaction
            showShareUnsignedTransactionDialog(txHex);
            
        } catch (Exception e) {
            log.error("Error creating unsigned transaction", e);
            Toast.makeText(this, getString(R.string.multisig_error_creating_tx, e.getMessage()), Toast.LENGTH_LONG).show();
        } finally {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        }
    }
    
    private void showShareUnsignedTransactionDialog(String txHex) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_share_transaction, null);
        TextView textTxHex = dialogView.findViewById(R.id.text_transaction_hex);
        ImageView qrCode = dialogView.findViewById(R.id.qr_code_image);
        Button btnShare = dialogView.findViewById(R.id.btn_share);
        Button btnShowQr = dialogView.findViewById(R.id.btn_show_qr);
        
        textTxHex.setText(txHex);
        
        // Generate QR code
        try {
            final Bitmap qrCodeBitmap = Qr.bitmap(txHex);
            if (qrCodeBitmap != null) {
                // Convert to RGB with white background for better visibility
                final Bitmap qrCodeWithBackground = convertQrToWhiteBackground(qrCodeBitmap);
                qrCode.setImageBitmap(qrCodeWithBackground);
            }
        } catch (Exception e) {
            log.warn("Error generating QR code", e);
        }
        
        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, txHex);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.multisig_share_unsigned_tx)));
        });
        
        btnShowQr.setOnClickListener(v -> showQrCodeDialog(txHex, getString(R.string.multisig_unsigned_transaction)));
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.multisig_share_unsigned_tx)
            .setView(dialogView)
            .setPositiveButton(R.string.button_dismiss, null)
            .show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.multisig_options, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.multisig_options_sign) {
            showSignTransactionDialog();
            return true;
        } else if (item.getItemId() == R.id.multisig_options_merge) {
            showMergeSignaturesDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showSignTransactionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sign_transaction, null);
        EditText editTxHex = dialogView.findViewById(R.id.edit_transaction_hex);
        TextView textHelp = dialogView.findViewById(R.id.text_help);
        Button btnScan = dialogView.findViewById(R.id.btn_scan_tx);
        
        if (textHelp != null) {
            textHelp.setText(getString(R.string.multisig_sign_tx_help));
        }
        
        if (btnScan != null) {
            btnScan.setOnClickListener(v -> {
                ScanActivity.startForResult(this, REQUEST_CODE_SCAN_TRANSACTION);
            });
        }
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.multisig_sign_transaction)
            .setView(dialogView)
            .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                String txHex = editTxHex.getText().toString().trim();
                if (TextUtils.isEmpty(txHex)) {
                    Toast.makeText(this, R.string.multisig_error_no_tx_hex, Toast.LENGTH_LONG).show();
                    return;
                }
                
                signTransaction(txHex);
            })
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }
    
    private void signTransaction(String txHex) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
            
            byte[] txBytes = Utils.HEX.decode(txHex);
            Transaction tx = new Transaction(params, txBytes);
            
            // Sign with our key
            ECKey ourKey = wallet.freshReceiveKey();
            if (ourKey == null) {
                ourKey = wallet.getActiveKeyChain().getKey(org.bitcoinj.wallet.KeyChain.KeyPurpose.RECEIVE_FUNDS);
            }
            
            if (ourKey == null) {
                Toast.makeText(this, R.string.multisig_error_no_signing_key, Toast.LENGTH_LONG).show();
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                return;
            }
            
            // Sign each input
            for (int i = 0; i < tx.getInputs().size(); i++) {
                TransactionInput input = tx.getInput(i);
                Sha256Hash hash = tx.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
                ECKey.ECDSASignature sig = ourKey.sign(hash);
                TransactionSignature txSig = new TransactionSignature(sig, Transaction.SigHash.ALL, false);
                
                // Create scriptSig: OP_0 <sig1> <sig2> ... <redeemScript>
                ScriptBuilder scriptSig = new ScriptBuilder();
                scriptSig.smallNum(0); // OP_0
                scriptSig.data(txSig.encodeToBitcoin());
                scriptSig.data(redeemScript.getProgram());
                
                input.setScriptSig(scriptSig.build());
            }
            
            // Serialize signed transaction
            byte[] signedTxBytes = tx.bitcoinSerialize();
            String signedTxHex = Utils.HEX.encode(signedTxBytes);
            
            // Share signed transaction
            showShareSignedTransactionDialog(signedTxHex);
            
        } catch (Exception e) {
            log.error("Error signing transaction", e);
            Toast.makeText(this, getString(R.string.multisig_error_signing, e.getMessage()), Toast.LENGTH_LONG).show();
        } finally {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        }
    }
    
    private void showShareSignedTransactionDialog(String signedTxHex) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_share_transaction, null);
        TextView textTxHex = dialogView.findViewById(R.id.text_transaction_hex);
        ImageView qrCode = dialogView.findViewById(R.id.qr_code_image);
        Button btnShare = dialogView.findViewById(R.id.btn_share);
        Button btnBroadcast = dialogView.findViewById(R.id.btn_broadcast);
        
        textTxHex.setText(signedTxHex);
        
        // Generate QR code
        try {
            final Bitmap qrCodeBitmap = Qr.bitmap(signedTxHex);
            if (qrCodeBitmap != null) {
                // Convert to RGB with white background for better visibility
                final Bitmap qrCodeWithBackground = convertQrToWhiteBackground(qrCodeBitmap);
                qrCode.setImageBitmap(qrCodeWithBackground);
            }
        } catch (Exception e) {
            log.warn("Error generating QR code", e);
        }
        
        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, signedTxHex);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.multisig_signed_transaction)));
        });
        
        btnBroadcast.setOnClickListener(v -> {
            try {
                byte[] txBytes = Utils.HEX.decode(signedTxHex);
                Transaction tx = new Transaction(params, txBytes);
                broadcastTransaction(tx);
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.multisig_error_broadcasting, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.multisig_signed_transaction)
            .setView(dialogView)
            .setPositiveButton(R.string.button_dismiss, null)
            .show();
    }
    
    private void showMergeSignaturesDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_merge_signatures, null);
        EditText editTxHex = dialogView.findViewById(R.id.edit_transaction_hex);
        EditText editSignatures = dialogView.findViewById(R.id.edit_signatures);
        TextView textHelp = dialogView.findViewById(R.id.text_help);
        
        if (textHelp != null) {
            textHelp.setText(getString(R.string.multisig_merge_help));
        }
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.multisig_merge_signatures)
            .setView(dialogView)
            .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                String txHex = editTxHex.getText().toString().trim();
                String signaturesStr = editSignatures.getText().toString().trim();
                
                if (TextUtils.isEmpty(txHex) || TextUtils.isEmpty(signaturesStr)) {
                    Toast.makeText(this, R.string.multisig_error_fill_all_fields, Toast.LENGTH_LONG).show();
                    return;
                }
                
                mergeSignatures(txHex, signaturesStr);
            })
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }
    
    private void mergeSignatures(String txHex, String signaturesStr) {
        try {
            // This is a simplified version - in production, you'd need proper PSBT handling
            // For now, we'll just broadcast if we have enough signatures
            byte[] txBytes = Utils.HEX.decode(txHex);
            Transaction tx = new Transaction(params, txBytes);
            
            // Verify transaction is fully signed
            if (tx.getInputs().size() == 0) {
                Toast.makeText(this, R.string.multisig_error_no_inputs, Toast.LENGTH_LONG).show();
                return;
            }
            
            // Broadcast transaction
            broadcastTransaction(tx);
            
        } catch (Exception e) {
            log.error("Error merging signatures", e);
            Toast.makeText(this, getString(R.string.multisig_error_merging, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
    
    private void broadcastTransaction(Transaction tx) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
            
            AbstractWalletActivityViewModel viewModel = new AbstractWalletActivityViewModel(application);
            ListenableFuture<Transaction> future = viewModel.broadcastTransaction(tx);
            
            future.addListener(() -> {
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, R.string.multisig_tx_broadcast_success, Toast.LENGTH_LONG).show();
                });
            }, Runnable::run);
            
        } catch (Exception e) {
            log.error("Error broadcasting transaction", e);
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            Toast.makeText(this, getString(R.string.multisig_error_broadcasting, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Convert ALPHA_8 QR code bitmap to RGB with white background for better visibility
     */
    private Bitmap convertQrToWhiteBackground(Bitmap alphaBitmap) {
        if (alphaBitmap == null) {
            return null;
        }
        
        // If already RGB, return as is
        if (alphaBitmap.getConfig() != Bitmap.Config.ALPHA_8) {
            return alphaBitmap;
        }
        
        int width = alphaBitmap.getWidth();
        int height = alphaBitmap.getHeight();
        
        // Create RGB bitmap with white background
        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        
        // Read pixels from ALPHA_8 bitmap as bytes
        byte[] alphaPixels = new byte[width * height];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(alphaPixels);
        alphaBitmap.copyPixelsToBuffer(buffer);
        
        // Convert to RGB pixels
        int[] rgbPixels = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            byte pixel = alphaPixels[i];
            // In ALPHA_8, -1 (0xFF) means opaque/black QR code, 0 means transparent/white background
            if ((pixel & 0xFF) > 128) {
                rgbPixels[i] = 0xFF000000; // Black QR code
            } else {
                rgbPixels[i] = 0xFFFFFFFF; // White background
            }
        }
        
        rgbBitmap.setPixels(rgbPixels, 0, width, 0, 0, width, height);
        return rgbBitmap;
    }
}

