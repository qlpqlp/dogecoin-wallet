package de.schildbach.wallet.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.DigitalSignature;
import de.schildbach.wallet.Constants;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.wallet.DeterministicKeyChain;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;

import android.provider.MediaStore;
import android.os.Environment;
import androidx.core.content.FileProvider;
import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * Activity to view, edit tags, and delete digital signatures
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class DigitalSignaturesListActivity extends AbstractWalletActivity {
    
    private static final Logger log = LoggerFactory.getLogger(DigitalSignaturesListActivity.class);
    
    private RecyclerView recyclerSignatures;
    private LinearLayout layoutEmptyState;
    private Button btnSign;
    private Button btnVerify;
    private DigitalSignaturesAdapter adapter;
    private AddressBookDatabase database;
    private List<DigitalSignature> signatures = new ArrayList<>();
    
    // For signing/verification
    private WalletApplication application;
    private ECKey signingKey;
    private String selectedFilePath;
    private Uri selectedFileUri;
    private String currentPhotoPath;
    private static final int FILE_PICKER_REQUEST_CODE = 1001;
    private static final int REQUEST_CODE_TAKE_PHOTO = 1002;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_digital_signatures_list);
        
        // Initialize database
        database = AddressBookDatabase.getDatabase(this);
        
        // Initialize application and signing key
        application = (WalletApplication) getApplication();
        initializeSigningKey();
        
        // Ensure signed folder exists
        ensureSignedFolderExists();
        
        // Initialize views
        recyclerSignatures = findViewById(R.id.recycler_signatures);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        btnSign = findViewById(R.id.btn_sign);
        btnVerify = findViewById(R.id.btn_verify);
        
        // Setup RecyclerView
        recyclerSignatures.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DigitalSignaturesAdapter();
        recyclerSignatures.setAdapter(adapter);
        
        // Setup button listeners
        btnSign.setOnClickListener(v -> showSignChoiceDialog());
        btnVerify.setOnClickListener(v -> showVerifyDialog());
        
        // Load signatures
        loadSignatures();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload signatures when returning to this activity
        loadSignatures();
    }
    
    private void loadSignatures() {
        try {
            signatures = database.digitalSignatureDao().getAllSignatures();
            adapter.notifyDataSetChanged();
            
            if (signatures.isEmpty()) {
                layoutEmptyState.setVisibility(View.VISIBLE);
                recyclerSignatures.setVisibility(View.GONE);
            } else {
                layoutEmptyState.setVisibility(View.GONE);
                recyclerSignatures.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            log.error("Error loading signatures", e);
            Toast.makeText(this, "Error loading signatures: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showEditTagDialog(DigitalSignature signature, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_tag, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        EditText editTag = dialogView.findViewById(R.id.edit_tag);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        // Set current tag
        if (!TextUtils.isEmpty(signature.getTag())) {
            editTag.setText(signature.getTag());
        }
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String newTag = editTag.getText().toString().trim();
            signature.setTag(TextUtils.isEmpty(newTag) ? null : newTag);
            
            try {
                database.digitalSignatureDao().updateSignature(signature);
                adapter.notifyItemChanged(position);
                Toast.makeText(this, "Tag updated successfully", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Exception e) {
                log.error("Error updating tag", e);
                Toast.makeText(this, "Error updating tag: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        dialog.show();
    }
    
    private void showSignatureDetails(DigitalSignature signature) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_signature, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Get views
        TextView textType = dialogView.findViewById(R.id.text_type);
        TextView textTagLabel = dialogView.findViewById(R.id.text_tag_label);
        TextView textTag = dialogView.findViewById(R.id.text_tag);
        TextView textTagInputLabel = dialogView.findViewById(R.id.text_tag_input_label);
        EditText editTagInput = dialogView.findViewById(R.id.edit_tag_input);
        TextView textContentLabel = dialogView.findViewById(R.id.text_content_label);
        TextView textContent = dialogView.findViewById(R.id.text_content);
        LinearLayout layoutFileHash = dialogView.findViewById(R.id.layout_file_hash);
        TextView textFileHash = dialogView.findViewById(R.id.text_file_hash);
        TextView textAddress = dialogView.findViewById(R.id.text_address);
        TextView textSignature = dialogView.findViewById(R.id.text_signature);
        ImageButton btnDownloadFile = dialogView.findViewById(R.id.btn_download_file);
        ImageButton btnCopySignature = dialogView.findViewById(R.id.btn_copy_signature);
        ImageButton btnShareSignature = dialogView.findViewById(R.id.btn_share_signature);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        // Hide tag input fields (only for viewing existing signatures)
        textTagInputLabel.setVisibility(View.GONE);
        editTagInput.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        
        // Set type
        String typeText = signature.getType().substring(0, 1).toUpperCase() + signature.getType().substring(1);
        textType.setText(typeText);
        
        // Set tag (show only if exists)
        if (!TextUtils.isEmpty(signature.getTag())) {
            textTagLabel.setVisibility(View.VISIBLE);
            textTag.setVisibility(View.VISIBLE);
            textTag.setText(signature.getTag());
        } else {
            textTagLabel.setVisibility(View.GONE);
            textTag.setVisibility(View.GONE);
        }
        
        // Set content based on type
        if ("photo".equals(signature.getType())) {
            textContentLabel.setText("Photo Path:");
            textContent.setText(signature.getContent() != null ? signature.getContent() : "N/A");
        } else if ("file".equals(signature.getType())) {
            textContentLabel.setText("File Path:");
            textContent.setText(signature.getContent() != null ? signature.getContent() : "N/A");
        } else {
            textContentLabel.setText("Signed Text:");
            textContent.setText(signature.getContent() != null ? signature.getContent() : "N/A");
        }
        
        // Set file hash (show only for file/photo signatures)
        if (!TextUtils.isEmpty(signature.getFileHash())) {
            layoutFileHash.setVisibility(View.VISIBLE);
            textFileHash.setText(signature.getFileHash());
        } else {
            layoutFileHash.setVisibility(View.GONE);
        }
        
        // Set address
        textAddress.setText(signature.getAddress());
        
        // Set signature
        textSignature.setText(signature.getSignature());
        
        // Show/hide download button based on signature type
        if ("file".equals(signature.getType()) || "photo".equals(signature.getType())) {
            // Show download button for file/photo signatures
            btnDownloadFile.setVisibility(View.VISIBLE);
            
            // Setup download button listener
            btnDownloadFile.setOnClickListener(v -> {
                if (signature.getContent() != null && !signature.getContent().isEmpty()) {
                    File file = new File(signature.getContent());
                    if (file.exists() && file.isFile()) {
                        try {
                            Uri fileUri = FileProvider.getUriForFile(this,
                                getPackageName() + ".file_attachment", file);
                            
                            // Determine MIME type based on file extension
                            String mimeType = "application/octet-stream";
                            String fileName = file.getName().toLowerCase();
                            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                                mimeType = "image/jpeg";
                            } else if (fileName.endsWith(".png")) {
                                mimeType = "image/png";
                            } else if (fileName.endsWith(".pdf")) {
                                mimeType = "application/pdf";
                            } else if (fileName.endsWith(".txt")) {
                                mimeType = "text/plain";
                            } else if (fileName.endsWith(".mp4") || fileName.endsWith(".mov")) {
                                mimeType = "video/mp4";
                            }
                            
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType(mimeType);
                            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            
                            // Create chooser with descriptive title
                            Intent chooser = Intent.createChooser(shareIntent, "Download File");
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(chooser);
                            
                            Toast.makeText(this, "Opening file...", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            log.error("Error sharing file", e);
                            Toast.makeText(this, "Error opening file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, "File not found at: " + signature.getContent(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "No file path available", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Hide download button for text signatures
            btnDownloadFile.setVisibility(View.GONE);
        }
        
        // Setup button listeners
        btnCopySignature.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Signature", signature.getSignature());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Signature copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        
        btnShareSignature.setOnClickListener(v -> {
            StringBuilder shareText = new StringBuilder();
            
            // Add tag if exists
            if (!TextUtils.isEmpty(signature.getTag())) {
                shareText.append("Tag: ").append(signature.getTag()).append("\n\n");
            }
            
            // Add content
            if ("photo".equals(signature.getType())) {
                shareText.append("Photo Path: ").append(signature.getContent() != null ? signature.getContent() : "N/A");
            } else if ("file".equals(signature.getType())) {
                shareText.append("File Path: ").append(signature.getContent() != null ? signature.getContent() : "N/A");
                if (!TextUtils.isEmpty(signature.getFileHash())) {
                    shareText.append("\nFile Hash (SHA256): ").append(signature.getFileHash());
                }
            } else {
                shareText.append("Signed Text:\n").append(signature.getContent() != null ? signature.getContent() : "N/A");
            }
            
            shareText.append("\n\nDoge Address: ").append(signature.getAddress());
            shareText.append("\n\nDigital Signature:\n").append(signature.getSignature());
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
            startActivity(Intent.createChooser(shareIntent, "Share Signature"));
        });
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void showDeleteConfirmation(DigitalSignature signature, int position) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Signature")
            .setMessage("Are you sure you want to delete this signature? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                try {
                    database.digitalSignatureDao().deleteSignature(signature);
                    signatures.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, signatures.size());
                    
                    if (signatures.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                        recyclerSignatures.setVisibility(View.GONE);
                    }
                    
                    Toast.makeText(this, "Signature deleted", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    log.error("Error deleting signature", e);
                    Toast.makeText(this, "Error deleting signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showSignChoiceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sign_choice, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        Button btnSignText = dialogView.findViewById(R.id.btn_sign_text);
        Button btnSignFile = dialogView.findViewById(R.id.btn_sign_file);
        
        btnSignText.setOnClickListener(v -> {
            dialog.dismiss();
            showSignTextDialog();
        });
        
        btnSignFile.setOnClickListener(v -> {
            dialog.dismiss();
            showSignFileDialog();
        });
        
        dialog.show();
    }
    
    private void showSignTextDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sign_text, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        EditText editTextToSign = dialogView.findViewById(R.id.edit_text_to_sign);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnGenerateSignature = dialogView.findViewById(R.id.btn_generate_signature);
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnGenerateSignature.setOnClickListener(v -> {
            String text = editTextToSign.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(this, "Please enter text to sign", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (signingKey == null) {
                Toast.makeText(this, "No private key available for signing", Toast.LENGTH_LONG).show();
                return;
            }
            
            try {
                String signature = signText(text);
                Address address = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, signingKey);
                String addressString = address.toString();
                
                // Show results dialog
                showSignatureResultDialog(signature, addressString, text, null, false);
                dialog.dismiss();
            } catch (Exception e) {
                log.error("Error generating signature", e);
                Toast.makeText(this, "Error generating signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        dialog.show();
    }
    
    private AlertDialog currentSignFileDialog;
    private TextView currentTextSelectedFile;
    private Button currentBtnSignSelectedFile;
    
    private void showSignFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_sign_file, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        currentSignFileDialog = dialog;
        
        TextView textSelectedFile = dialogView.findViewById(R.id.text_selected_file);
        currentTextSelectedFile = textSelectedFile;
        Button btnSelectFile = dialogView.findViewById(R.id.btn_select_file);
        Button btnTakePhoto = dialogView.findViewById(R.id.btn_take_photo);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSignSelectedFile = dialogView.findViewById(R.id.btn_sign_selected_file);
        currentBtnSignSelectedFile = btnSignSelectedFile;
        
        btnSelectFile.setOnClickListener(v -> selectFile(dialog));
        btnTakePhoto.setOnClickListener(v -> takePhoto(dialog));
        btnCancel.setOnClickListener(v -> {
            selectedFileUri = null;
            selectedFilePath = null;
            currentPhotoPath = null;
            dialog.dismiss();
        });
        btnSignSelectedFile.setOnClickListener(v -> {
            if (selectedFileUri == null) {
                Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (signingKey == null) {
                Toast.makeText(this, "No private key available for signing", Toast.LENGTH_LONG).show();
                return;
            }
            
            try {
                String fileHash = calculateFileHash(selectedFileUri);
                if (TextUtils.isEmpty(fileHash)) {
                    Toast.makeText(this, "Could not calculate file hash", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String signature = signText(fileHash);
                Address address = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, signingKey);
                String addressString = address.toString();
                
                // Show results dialog
                showSignatureResultDialog(signature, addressString, selectedFilePath, fileHash, true);
                dialog.dismiss();
            } catch (Exception e) {
                log.error("Error generating signature from file", e);
                Toast.makeText(this, "Error generating signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        dialog.show();
    }
    
    private void showVerifyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_verify_signature, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        EditText editOriginalText = dialogView.findViewById(R.id.edit_original_text);
        EditText editSignature = dialogView.findViewById(R.id.edit_signature);
        EditText editVerificationAddress = dialogView.findViewById(R.id.edit_verification_address);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnVerifySignature = dialogView.findViewById(R.id.btn_verify_signature);
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnVerifySignature.setOnClickListener(v -> {
            String originalText = editOriginalText.getText().toString().trim();
            String signature = editSignature.getText().toString().trim();
            String address = editVerificationAddress.getText().toString().trim();
            
            if (TextUtils.isEmpty(originalText) || TextUtils.isEmpty(signature) || TextUtils.isEmpty(address)) {
                Toast.makeText(this, "Please enter original text, signature, and Dogecoin address", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                boolean isValid = verifyTextSignature(originalText, signature, address);
                if (isValid) {
                    Toast.makeText(this, "Signature verified successfully!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Invalid signature!", Toast.LENGTH_LONG).show();
                }
                dialog.dismiss();
            } catch (Exception e) {
                log.error("Error verifying signature", e);
                Toast.makeText(this, "Error verifying signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        dialog.show();
    }
    
    private void showSignatureResultDialog(String signature, String address, String content, String fileHash, boolean isFile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_signature, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        TextView textSignature = dialogView.findViewById(R.id.text_signature);
        TextView textAddress = dialogView.findViewById(R.id.text_address);
        TextView textContent = dialogView.findViewById(R.id.text_content);
        TextView textContentLabel = dialogView.findViewById(R.id.text_content_label);
        LinearLayout layoutFileHash = dialogView.findViewById(R.id.layout_file_hash);
        TextView textFileHash = dialogView.findViewById(R.id.text_file_hash);
        EditText editTagInput = dialogView.findViewById(R.id.edit_tag_input);
        TextView textTagLabel = dialogView.findViewById(R.id.text_tag_label);
        TextView textTag = dialogView.findViewById(R.id.text_tag);
        ImageButton btnCopySignature = dialogView.findViewById(R.id.btn_copy_signature);
        ImageButton btnShareSignature = dialogView.findViewById(R.id.btn_share_signature);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        // Show save button and tag input for new signatures
        btnSave.setVisibility(View.VISIBLE);
        editTagInput.setVisibility(View.VISIBLE);
        // Hide tag display (for existing signatures)
        textTagLabel.setVisibility(View.GONE);
        textTag.setVisibility(View.GONE);
        
        textSignature.setText(signature);
        textAddress.setText(address);
        
        if (isFile) {
            textContentLabel.setText("File Path:");
            textContent.setText(content);
            if (fileHash != null) {
                layoutFileHash.setVisibility(View.VISIBLE);
                textFileHash.setText(fileHash);
            }
        } else {
            textContentLabel.setText("Signed Text:");
            textContent.setText(content);
            layoutFileHash.setVisibility(View.GONE);
        }
        
        btnCopySignature.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Signature", signature);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Signature copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        
        btnShareSignature.setOnClickListener(v -> {
            StringBuilder shareText = new StringBuilder();
            if (isFile) {
                shareText.append("File Path: ").append(content).append("\n");
                if (fileHash != null) {
                    shareText.append("File Hash (SHA256): ").append(fileHash).append("\n");
                }
            } else {
                shareText.append("Message: ").append(content).append("\n");
            }
            shareText.append("\nDoge Address: ").append(address);
            shareText.append("\n\nDigital Signature:\n").append(signature);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
            startActivity(Intent.createChooser(shareIntent, "Share Signature"));
        });
        
        btnSave.setOnClickListener(v -> {
            try {
                // Get tag from input field
                String tag = editTagInput.getText().toString().trim();
                
                DigitalSignature sigRecord = new DigitalSignature();
                sigRecord.setSignature(signature);
                sigRecord.setAddress(address);
                sigRecord.setTag(TextUtils.isEmpty(tag) ? null : tag);
                
                if (isFile) {
                    if (selectedFilePath != null && selectedFilePath.contains("SIGNED_")) {
                        sigRecord.setType("photo");
                        sigRecord.setContent(selectedFilePath);
                    } else {
                        sigRecord.setType("file");
                        sigRecord.setContent(selectedFilePath);
                    }
                    sigRecord.setFileHash(fileHash);
                } else {
                    sigRecord.setType("text");
                    sigRecord.setContent(content);
                    sigRecord.setFileHash(null);
                }
                
                sigRecord.setTimestamp(System.currentTimeMillis());
                
                long id = database.digitalSignatureDao().insertSignature(sigRecord);
                if (id > 0) {
                    Toast.makeText(this, "Signature saved successfully", Toast.LENGTH_SHORT).show();
                    loadSignatures();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "Error saving signature", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                log.error("Error saving signature", e);
                Toast.makeText(this, "Error saving signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    // Helper methods for signing/verification (copied from DigitalSignatureActivity)
    private void initializeSigningKey() {
        application.getWalletAsync(wallet -> {
            try {
                if (wallet != null && wallet.getActiveKeyChain() != null) {
                    DeterministicKeyChain keyChain = wallet.getActiveKeyChain();
                    List<ChildNumber> path = new ArrayList<>(keyChain.getAccountPath());
                    path.add(new ChildNumber(0));
                    DeterministicKey key = keyChain.getKeyByPath(path, true);
                    signingKey = key;
                    Address address = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, key);
                    log.info("Initialized signing key: {}", address.toString());
                } else {
                    log.warn("No wallet or keychain available for signing");
                }
            } catch (Exception e) {
                log.error("Error initializing signing key", e);
            }
        });
    }
    
    private String signText(String text) throws Exception {
        String messagePrefix = "Dogecoin Signed Message:\n";
        byte[] messageBytes = text.getBytes(StandardCharsets.UTF_8);
        Sha256Hash messageHash = createStandardMessageHash(messagePrefix, messageBytes);
        ECKey.ECDSASignature signature = signingKey.sign(messageHash);
        byte[] rawSignature = createRawSignature(signature, messageHash);
        return android.util.Base64.encodeToString(rawSignature, android.util.Base64.NO_WRAP);
    }
    
    private byte[] createRawSignature(ECKey.ECDSASignature signature, Sha256Hash messageHash) {
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            try {
                ECKey recoveredKey = ECKey.recoverFromSignature(i, signature, messageHash, signingKey.isCompressed());
                if (recoveredKey != null && java.util.Arrays.equals(recoveredKey.getPubKey(), signingKey.getPubKey())) {
                    recId = i;
                    break;
                }
            } catch (Exception e) {
                // Continue
            }
        }
        if (recId == -1) {
            throw new IllegalStateException("Could not find recId");
        }
        int headerByte = 27 + recId + (signingKey.isCompressed() ? 4 : 0);
        byte[] r = Utils.bigIntegerToBytes(signature.r, 32);
        byte[] s = Utils.bigIntegerToBytes(signature.s, 32);
        ByteBuffer out = ByteBuffer.allocate(65);
        out.put((byte) headerByte);
        out.put(r);
        out.put(s);
        return out.array();
    }
    
    private Sha256Hash createStandardMessageHash(String prefix, byte[] message) {
        try {
            byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
            byte[] prefixLen = varInt(prefixBytes.length);
            byte[] msgLen = varInt(message.length);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(prefixLen);
            baos.write(prefixBytes);
            baos.write(msgLen);
            baos.write(message);
            byte[] fullMessage = baos.toByteArray();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] firstHash = sha256.digest(fullMessage);
            byte[] secondHash = sha256.digest(firstHash);
            return Sha256Hash.wrap(secondHash);
        } catch (Exception e) {
            throw new RuntimeException("Error creating message hash", e);
        }
    }
    
    private byte[] varInt(long val) {
        if (val < 0xFDL) {
            return new byte[]{(byte) val};
        } else if (val <= 0xFFFFL) {
            ByteBuffer bb = ByteBuffer.allocate(3);
            bb.put((byte) 0xFD);
            bb.putShort((short) val);
            return bb.array();
        } else if (val <= 0xFFFFFFFFL) {
            ByteBuffer bb = ByteBuffer.allocate(5);
            bb.put((byte) 0xFE);
            bb.putInt((int) val);
            return bb.array();
        } else {
            ByteBuffer bb = ByteBuffer.allocate(9);
            bb.put((byte) 0xFF);
            bb.putLong(val);
            return bb.array();
        }
    }
    
    private boolean verifyTextSignature(String text, String signatureInput, String address) throws Exception {
        try {
            String messagePrefix = "Dogecoin Signed Message:\n";
            byte[] messageBytes = text.getBytes(StandardCharsets.UTF_8);
            Sha256Hash messageHash = createStandardMessageHash(messagePrefix, messageBytes);
            byte[] signatureBytes;
            ECKey.ECDSASignature signature;
            try {
                String paddedSignature = signatureInput;
                while (paddedSignature.length() % 4 != 0) {
                    paddedSignature += "=";
                }
                signatureBytes = android.util.Base64.decode(paddedSignature, android.util.Base64.DEFAULT);
                if (signatureBytes.length == 65) {
                    byte[] rBytes = new byte[32];
                    byte[] sBytes = new byte[32];
                    System.arraycopy(signatureBytes, 1, rBytes, 0, 32);
                    System.arraycopy(signatureBytes, 33, sBytes, 0, 32);
                    signature = new ECKey.ECDSASignature(
                        new java.math.BigInteger(1, rBytes),
                        new java.math.BigInteger(1, sBytes)
                    );
                } else {
                    signature = ECKey.ECDSASignature.decodeFromDER(signatureBytes);
                }
            } catch (Exception e1) {
                signatureBytes = Utils.HEX.decode(signatureInput);
                if (signatureBytes.length == 65) {
                    byte[] rBytes = new byte[32];
                    byte[] sBytes = new byte[32];
                    System.arraycopy(signatureBytes, 1, rBytes, 0, 32);
                    System.arraycopy(signatureBytes, 33, sBytes, 0, 32);
                    signature = new ECKey.ECDSASignature(
                        new java.math.BigInteger(1, rBytes),
                        new java.math.BigInteger(1, sBytes)
                    );
                } else {
                    signature = ECKey.ECDSASignature.decodeFromDER(signatureBytes);
                }
            }
            Address addressObj = LegacyAddress.fromBase58(Constants.NETWORK_PARAMETERS, address);
            ECKey recoveredKey = null;
            Address recoveredAddress = null;
            boolean addressMatches = false;
            for (int recId = 0; recId < 4; recId++) {
                try {
                    recoveredKey = ECKey.recoverFromSignature(recId, signature, messageHash, true);
                    if (recoveredKey != null) {
                        recoveredAddress = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, recoveredKey);
                        addressMatches = recoveredAddress.toString().equals(address);
                        if (addressMatches) break;
                    }
                } catch (Exception e) {
                    // Continue
                }
                try {
                    recoveredKey = ECKey.recoverFromSignature(recId, signature, messageHash, false);
                    if (recoveredKey != null) {
                        recoveredAddress = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, recoveredKey);
                        addressMatches = recoveredAddress.toString().equals(address);
                        if (addressMatches) break;
                    }
                } catch (Exception e) {
                    // Continue
                }
            }
            if (recoveredKey == null || !addressMatches) {
                return false;
            }
            return ECKey.verify(messageHash.getBytes(), signature, recoveredKey.getPubKey());
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return false;
        }
    }
    
    private String calculateFileHash(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                sha256.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = sha256.digest();
            return Utils.HEX.encode(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private void selectFile(AlertDialog dialog) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select File to Sign"), FILE_PICKER_REQUEST_CODE);
    }
    
    private void takePhoto(AlertDialog dialog) {
        try {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 1003);
                return;
            }
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
                Toast.makeText(this, "No camera app available on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoPath = photoFile.getAbsolutePath();
                Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".file_attachment", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
            }
        } catch (Exception e) {
            log.error("Error taking photo", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private File createImageFile() throws IOException {
        File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            throw new IOException("External pictures directory not available");
        }
        File signedFolder = new File(picturesDir, "signed");
        if (!signedFolder.exists()) {
            signedFolder.mkdirs();
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "SIGNED_" + timeStamp + ".jpg";
        return new File(signedFolder, imageFileName);
    }
    
    private void ensureSignedFolderExists() {
        try {
            File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir != null) {
                File signedFolder = new File(picturesDir, "signed");
                if (!signedFolder.exists()) {
                    signedFolder.mkdirs();
                }
            }
        } catch (Exception e) {
            log.error("Error ensuring signed folder exists", e);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1003) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Retry photo capture - show dialog again
                showSignFileDialog();
            } else {
                Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            selectedFilePath = selectedFileUri.toString();
            if (currentTextSelectedFile != null) {
                currentTextSelectedFile.setText("File: " + selectedFileUri.getLastPathSegment());
            }
            if (currentBtnSignSelectedFile != null) {
                currentBtnSignSelectedFile.setEnabled(true);
            }
        } else if (requestCode == REQUEST_CODE_TAKE_PHOTO && resultCode == RESULT_OK) {
            if (currentPhotoPath != null) {
                File photoFile = new File(currentPhotoPath);
                if (photoFile.exists() && photoFile.length() > 0) {
                    selectedFileUri = FileProvider.getUriForFile(this, getPackageName() + ".file_attachment", photoFile);
                    selectedFilePath = currentPhotoPath;
                    if (currentTextSelectedFile != null) {
                        currentTextSelectedFile.setText("Photo: " + photoFile.getName());
                    }
                    if (currentBtnSignSelectedFile != null) {
                        currentBtnSignSelectedFile.setEnabled(true);
                    }
                }
            }
        }
    }
    
    private class DigitalSignaturesAdapter extends RecyclerView.Adapter<DigitalSignaturesAdapter.SignatureViewHolder> {
        
        @NonNull
        @Override
        public SignatureViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_digital_signature, parent, false);
            return new SignatureViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull SignatureViewHolder holder, int position) {
            DigitalSignature signature = signatures.get(position);
            holder.bind(signature, position);
        }
        
        @Override
        public int getItemCount() {
            return signatures.size();
        }
        
        class SignatureViewHolder extends RecyclerView.ViewHolder {
            private TextView textType;
            private TextView textTag;
            private TextView textContent;
            private TextView textAddress;
            private TextView textTimestamp;
            private Button btnView;
            private Button btnEditTag;
            private Button btnDelete;
            
            public SignatureViewHolder(@NonNull View itemView) {
                super(itemView);
                textType = itemView.findViewById(R.id.text_type);
                textTag = itemView.findViewById(R.id.text_tag);
                textContent = itemView.findViewById(R.id.text_content);
                textAddress = itemView.findViewById(R.id.text_address);
                textTimestamp = itemView.findViewById(R.id.text_timestamp);
                btnView = itemView.findViewById(R.id.btn_view);
                btnEditTag = itemView.findViewById(R.id.btn_edit_tag);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
            
            public void bind(DigitalSignature signature, int position) {
                // Set type with icon
                String typeText = signature.getType().substring(0, 1).toUpperCase() + signature.getType().substring(1);
                textType.setText(typeText);
                
                // Set tag (or "No tag" if empty)
                if (!TextUtils.isEmpty(signature.getTag())) {
                    textTag.setText("Tag: " + signature.getTag());
                    textTag.setVisibility(View.VISIBLE);
                } else {
                    textTag.setText("No tag");
                    textTag.setVisibility(View.VISIBLE);
                }
                
                // Set content preview (truncate if too long)
                String content = signature.getContent();
                if (content != null && content.length() > 100) {
                    content = content.substring(0, 97) + "...";
                }
                textContent.setText(content != null ? content : "N/A");
                
                // Set address (shortened)
                String address = signature.getAddress();
                if (address.length() > 20) {
                    address = address.substring(0, 10) + "..." + address.substring(address.length() - 10);
                }
                textAddress.setText(address);
                
                // Set timestamp
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                textTimestamp.setText(dateFormat.format(new Date(signature.getTimestamp())));
                
                // Setup click listeners
                btnView.setOnClickListener(v -> showSignatureDetails(signature));
                btnEditTag.setOnClickListener(v -> showEditTagDialog(signature, position));
                btnDelete.setOnClickListener(v -> showDeleteConfirmation(signature, position));
            }
        }
    }
}

