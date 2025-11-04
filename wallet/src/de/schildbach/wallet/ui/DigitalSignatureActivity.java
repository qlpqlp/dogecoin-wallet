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

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.os.Environment;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.Crypto;
import de.schildbach.wallet.util.SecureMemory;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.DigitalSignature;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.security.NoSuchAlgorithmException;

/**
 * Activity for digital signature functionality using Dogecoin private keys
 * 
 * @author AI Assistant
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class DigitalSignatureActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(DigitalSignatureActivity.class);
    private static final int FILE_PICKER_REQUEST_CODE = 1001;
    private static final int REQUEST_CODE_TAKE_PHOTO = 1002;

    private WalletApplication application;
    private ECKey signingKey;
    private String selectedFilePath;
    private Uri selectedFileUri;
    private String currentPhotoPath; // Store the path of the taken photo
    private String currentSignedContent; // Store the content that was signed (text or file)
    private boolean isFileSignature; // Track if current signature is from file
    private String currentFileHash; // Store the SHA256 hash of the signed file

    // UI Components
    private LinearLayout panelSignText;
    private LinearLayout panelSignFile;
    private LinearLayout panelVerify;
    private LinearLayout panelResults;
    
    private EditText editTextToSign;
    private EditText editOriginalText;
    private EditText editSignature;
    private EditText editVerificationAddress;
    private EditText editTag;
    private TextView textSelectedFile;
    private TextView textSignatureResult;
    private TextView textAddressResult;
    private TextView textSignedTextLabel;
    private TextView textSignedTextResult;
    private TextView textFileHashLabel;
    private TextView textFileHashResult;
    
    private Button btnSignText;
    private Button btnSignFile;
    private Button btnVerify;
    private Button btnGenerateSignature;
    private Button btnTakePhoto;
    private Button btnSelectFile;
    private Button btnSignSelectedFile;
    private Button btnVerifySignature;
    private Button btnCopySignature;
    private Button btnShareSignature;
    private Button btnSaveSignature;
    private Button btnClearResults;
    private ImageButton btnViewSaved;
    
    // Database
    private AddressBookDatabase database;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_digital_signature);
        
        application = (WalletApplication) getApplication();
        
        // Initialize signing key from wallet
        initializeSigningKey();
        
        // Initialize UI components
        initializeViews();
        setupClickListeners();
        
        // Ensure signed folder exists
        ensureSignedFolderExists();
        
        // Check if we should show verify or sign panel based on intent
        String action = getIntent().getStringExtra("action");
        if ("verify".equals(action)) {
            // Show verify panel if action is verify
            showPanel(panelVerify);
        } else {
            // Show sign text panel by default
            showPanel(panelSignText);
        }
        
    }

    private void initializeSigningKey() {
        application.getWalletAsync(wallet -> {
            try {
                // Get the first private key from the wallet
                if (wallet != null && wallet.getActiveKeyChain() != null) {
                    DeterministicKeyChain keyChain = wallet.getActiveKeyChain();
                    // Get the first private key (index 0) for signing - this gives us a private key, not a watching key
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

    private void initializeViews() {
        // Panels
        panelSignText = findViewById(R.id.panel_sign_text);
        panelSignFile = findViewById(R.id.panel_sign_file);
        panelVerify = findViewById(R.id.panel_verify);
        panelResults = findViewById(R.id.panel_results);
        
        // EditTexts
        editTextToSign = findViewById(R.id.edit_text_to_sign);
        editOriginalText = findViewById(R.id.edit_original_text);
        editSignature = findViewById(R.id.edit_signature);
        editVerificationAddress = findViewById(R.id.edit_verification_address);
        editTag = findViewById(R.id.edit_tag);
        
    // TextViews
    textSelectedFile = findViewById(R.id.text_selected_file);
    textSignatureResult = findViewById(R.id.text_signature_result);
    textAddressResult = findViewById(R.id.text_address_result);
        textSignedTextLabel = findViewById(R.id.text_signed_text_label);
        textSignedTextResult = findViewById(R.id.text_signed_text_result);
        textFileHashLabel = findViewById(R.id.text_file_hash_label);
        textFileHashResult = findViewById(R.id.text_file_hash_result);
        
        // Buttons
        btnSignText = findViewById(R.id.btn_sign_text);
        btnSignFile = findViewById(R.id.btn_sign_file);
        btnVerify = findViewById(R.id.btn_verify);
        btnGenerateSignature = findViewById(R.id.btn_generate_signature);
        btnTakePhoto = findViewById(R.id.btn_take_photo);
        btnSelectFile = findViewById(R.id.btn_select_file);
        btnSignSelectedFile = findViewById(R.id.btn_sign_selected_file);
        btnVerifySignature = findViewById(R.id.btn_verify_signature);
        btnCopySignature = findViewById(R.id.btn_copy_signature);
        btnShareSignature = findViewById(R.id.btn_share_signature);
        btnSaveSignature = findViewById(R.id.btn_save_signature);
        btnClearResults = findViewById(R.id.btn_clear_results);
        btnViewSaved = findViewById(R.id.btn_view_saved);
        
        // Initialize database
        database = AddressBookDatabase.getDatabase(this);
    }

    private void setupClickListeners() {
        // Tab buttons
        btnSignText.setOnClickListener(v -> showPanel(panelSignText));
        btnSignFile.setOnClickListener(v -> showPanel(panelSignFile));
        btnVerify.setOnClickListener(v -> showPanel(panelVerify));
        
        // Sign text
        btnGenerateSignature.setOnClickListener(v -> generateSignatureFromText());
        
        // Sign file
        btnSelectFile.setOnClickListener(v -> selectFile());
        btnTakePhoto.setOnClickListener(v -> takePhoto());
        btnSignSelectedFile.setOnClickListener(v -> generateSignatureFromFile());
        
        // Verify signature
        btnVerifySignature.setOnClickListener(v -> verifySignature());
        
        // Results
        btnCopySignature.setOnClickListener(v -> copySignature());
        btnShareSignature.setOnClickListener(v -> shareSignature());
        btnSaveSignature.setOnClickListener(v -> saveSignatureToDatabase());
        btnClearResults.setOnClickListener(v -> clearResults());
        
        // View saved signatures
        btnViewSaved.setOnClickListener(v -> {
            Intent intent = new Intent(this, DigitalSignaturesListActivity.class);
            startActivity(intent);
        });
    }

    private void showPanel(LinearLayout panel) {
        // Hide all panels
        panelSignText.setVisibility(View.GONE);
        panelSignFile.setVisibility(View.GONE);
        panelVerify.setVisibility(View.GONE);
        
        // Reset all button backgrounds (remove highlight)
        btnSignText.setBackgroundTintList(null);
        btnSignFile.setBackgroundTintList(null);
        btnVerify.setBackgroundTintList(null);
        
        // Highlight the selected button
        if (panel == panelSignText) {
            btnSignText.setBackgroundTintList(getColorStateList(R.color.bg_level2));
        } else if (panel == panelSignFile) {
            btnSignFile.setBackgroundTintList(getColorStateList(R.color.bg_level2));
        } else if (panel == panelVerify) {
            btnVerify.setBackgroundTintList(getColorStateList(R.color.bg_level2));
        }
        
        // Show selected panel
        panel.setVisibility(View.VISIBLE);
    }

    private void generateSignatureFromText() {
        String text = editTextToSign.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Please enter text to sign", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (signingKey == null) {
            Toast.makeText(this, getString(R.string.digital_signature_error_no_private_key), Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            // Create signature
            String signature = signText(text);
            Address address = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, signingKey);
            String addressString = address.toString();
            
            // Store the signed content
            currentSignedContent = text;
            isFileSignature = false;
            
            // Show results
            showResults(signature, addressString);
            
        } catch (Exception e) {
            log.error("Error generating signature", e);
            Toast.makeText(this, "Error generating signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.digital_signature_file_picker_title)), FILE_PICKER_REQUEST_CODE);
    }
    
    private void takePhoto() {
        try {
            // Check camera permission first
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log.info("Camera permission not granted, requesting...");
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 1003);
                return;
            }
            log.info("Camera permission granted");
            
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            
            // Check if camera app is available
            if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
                log.warn("No camera app available");
                Toast.makeText(this, "No camera app available on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                log.error("Error creating photo file", ex);
                Toast.makeText(this, "Error creating photo file: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            
            if (photoFile != null) {
                currentPhotoPath = photoFile.getAbsolutePath();
                log.info("Photo path set to: {}", currentPhotoPath);
                
                Uri photoURI = FileProvider.getUriForFile(this,
                    "org.dogecoin.wallet.file_attachment", photoFile);
                log.info("Photo URI: {}", photoURI);
                
                // Try with FileProvider first
                try {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    log.info("Starting camera activity with FileProvider...");
                    log.info("Intent action: {}", takePictureIntent.getAction());
                    log.info("Intent extras: {}", takePictureIntent.getExtras());
                    log.info("Intent flags: {}", takePictureIntent.getFlags());
                    
                    startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
                    log.info("Camera activity started successfully with FileProvider");
                } catch (Exception e) {
                    log.warn("FileProvider approach failed, trying without output file", e);
                    // Fallback: try without specifying output file
                    takePictureIntent.removeExtra(MediaStore.EXTRA_OUTPUT);
                    takePictureIntent.removeFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    
                    try {
                        log.info("Starting camera activity without output file...");
                        startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
                        log.info("Camera started without output file");
                    } catch (Exception e2) {
                        log.error("Error starting camera activity", e2);
                        Toast.makeText(this, "Error starting camera: " + e2.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                log.error("Photo file is null");
                Toast.makeText(this, "Failed to create photo file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log.error("Error taking photo", e);
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    
    private File createImageFile() throws IOException {
        // Create "signed" folder in app's external files directory
        File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            throw new IOException("External pictures directory not available");
        }
        
        File signedFolder = new File(picturesDir, "signed");
        log.info("Signed folder path: {}", signedFolder.getAbsolutePath());
        
        if (!signedFolder.exists()) {
            boolean created = signedFolder.mkdirs();
            log.info("Created signed folder: {}, exists now: {}", created, signedFolder.exists());
            if (!created || !signedFolder.exists()) {
                throw new IOException("Failed to create signed folder: " + signedFolder.getAbsolutePath());
            }
        } else {
            log.info("Signed folder already exists: {}", signedFolder.getAbsolutePath());
        }
        
        // Verify folder is writable
        if (!signedFolder.canWrite()) {
            throw new IOException("Signed folder is not writable: " + signedFolder.getAbsolutePath());
        }
        
        // Create unique filename with timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "SIGNED_" + timeStamp + ".jpg";
        File image = new File(signedFolder, imageFileName);
        
        log.info("Creating photo file: {}", image.getAbsolutePath());
        
        // Try to create the file to ensure the path is valid
        try {
            if (image.createNewFile()) {
                log.info("Photo file created successfully");
                image.delete(); // Delete it immediately, camera will create it
            } else {
                log.warn("Photo file already exists, will be overwritten");
            }
        } catch (IOException e) {
            log.error("Failed to create photo file", e);
            throw new IOException("Failed to create photo file: " + image.getAbsolutePath(), e);
        }
        
        return image;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1003) { // Camera permission
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log.info("Camera permission granted, retrying photo capture");
                takePhoto();
            } else {
                log.warn("Camera permission denied");
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
            textSelectedFile.setText(selectedFilePath);
            btnSignSelectedFile.setEnabled(true);
        } else if (requestCode == REQUEST_CODE_TAKE_PHOTO) {
            log.info("Photo capture result: resultCode={}, currentPhotoPath={}", resultCode, currentPhotoPath);
            log.info("Result codes - RESULT_OK={}, RESULT_CANCELED={}", RESULT_OK, RESULT_CANCELED);
            log.info("Data intent: {}", data);
            if (data != null) {
                log.info("Data extras: {}", data.getExtras());
            }
            
            if (resultCode == RESULT_OK) {
                boolean photoHandled = false;
                
                // First, try to handle photo from our specified path
                if (currentPhotoPath != null) {
                    File photoFile = new File(currentPhotoPath);
                    log.info("Photo file exists immediately: {}, size: {}", photoFile.exists(), photoFile.length());
                    
                    // Sometimes the camera takes a moment to write the file, so wait a bit and check again
                    if (!photoFile.exists() || photoFile.length() == 0) {
                        log.info("Photo file not ready, waiting 1 second...");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        log.info("Photo file exists after wait: {}, size: {}", photoFile.exists(), photoFile.length());
                    }
                    
                    if (photoFile.exists() && photoFile.length() > 0) {
                        selectedFileUri = FileProvider.getUriForFile(this, 
                            "org.dogecoin.wallet.file_attachment", photoFile);
                        selectedFilePath = currentPhotoPath;
                        textSelectedFile.setText("Photo: " + photoFile.getName());
                        btnSignSelectedFile.setEnabled(true);
                        Toast.makeText(this, "Photo saved to signed folder", Toast.LENGTH_SHORT).show();
                        log.info("Photo selected successfully: {}", selectedFileUri);
                        photoHandled = true;
                    }
                }
                
                // If our path didn't work, try to get photo from data intent (fallback method)
                if (!photoHandled && data != null) {
                    log.info("Trying to handle photo from data intent: {}", data.getData());
                    
                    // Try to get photo from data.getData() first
                    if (data.getData() != null) {
                        try {
                            selectedFileUri = data.getData();
                            selectedFilePath = selectedFileUri.toString();
                            textSelectedFile.setText("Photo: " + selectedFileUri.getLastPathSegment());
                            btnSignSelectedFile.setEnabled(true);
                            Toast.makeText(this, "Photo captured successfully", Toast.LENGTH_SHORT).show();
                            log.info("Photo selected from data intent URI: {}", selectedFileUri);
                            photoHandled = true;
                        } catch (Exception e) {
                            log.error("Error handling photo from data intent URI", e);
                        }
                    }
                    
                    // If that didn't work, try to get bitmap from extras and save it
                    if (!photoHandled && data.getExtras() != null && data.getExtras().get("data") != null) {
                        try {
                            log.info("Trying to handle photo from bitmap data");
                            android.graphics.Bitmap bitmap = (android.graphics.Bitmap) data.getExtras().get("data");
                            if (bitmap != null) {
                                // Save bitmap to our file
                                File photoFile = new File(currentPhotoPath);
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(photoFile);
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
                                fos.close();
                                
                                selectedFileUri = FileProvider.getUriForFile(this,
                                    "org.dogecoin.wallet.file_attachment", photoFile);
                                selectedFilePath = currentPhotoPath;
                                textSelectedFile.setText("Photo: " + photoFile.getName());
                                btnSignSelectedFile.setEnabled(true);
                                Toast.makeText(this, "Photo saved to signed folder", Toast.LENGTH_SHORT).show();
                                log.info("Photo saved from bitmap to: {}", selectedFileUri);
                                photoHandled = true;
                            }
                        } catch (Exception e) {
                            log.error("Error handling photo from bitmap data", e);
                        }
                    }
                }
                
                if (!photoHandled) {
                    log.warn("No photo could be handled - currentPhotoPath={}, data={}", currentPhotoPath, data);
                    Toast.makeText(this, "Photo captured but could not be processed", Toast.LENGTH_SHORT).show();
                }
            } else {
                log.info("Photo capture cancelled or failed - resultCode: {}", resultCode);
                if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "Photo capture cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Photo capture failed (result code: " + resultCode + ")", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void generateSignatureFromFile() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (signingKey == null) {
            Toast.makeText(this, getString(R.string.digital_signature_error_no_private_key), Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            // Calculate file hash
            String fileHash = calculateFileHash(selectedFileUri);
            if (TextUtils.isEmpty(fileHash)) {
                Toast.makeText(this, "Could not calculate file hash", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create signature using the file hash
            String signature = signText(fileHash);
            Address address = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, signingKey);
            String addressString = address.toString();
            
            // Store the signed content and file hash
            currentSignedContent = fileHash;
            currentFileHash = fileHash;
            isFileSignature = true;
            
            // Show results
            showResults(signature, addressString);
            
        } catch (Exception e) {
            log.error("Error generating signature from file", e);
            Toast.makeText(this, "Error generating signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void verifySignature() {
        log.info("=== VERIFY SIGNATURE CALLED ===");
        String originalText = editOriginalText.getText().toString().trim();
        String signature = editSignature.getText().toString().trim();
        String address = editVerificationAddress.getText().toString().trim();
        
        log.info("Input - Text: '{}', Signature: '{}', Address: '{}'", originalText, signature, address);
        
        if (TextUtils.isEmpty(originalText) || TextUtils.isEmpty(signature) || TextUtils.isEmpty(address)) {
            log.warn("Missing required fields");
            Toast.makeText(this, "Please enter original text, signature, and Dogecoin address", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            log.info("Calling verifyTextSignature...");
            boolean isValid = verifyTextSignature(originalText, signature, address);
            log.info("Verification result: {}", isValid);
            
            if (isValid) {
                log.info("Signature is VALID!");
                Toast.makeText(this, getString(R.string.digital_signature_signature_verified), Toast.LENGTH_LONG).show();
                showResults(signature, "Signature verified successfully! Address: " + address);
            } else {
                log.warn("Signature is INVALID!");
                Toast.makeText(this, getString(R.string.digital_signature_signature_invalid), Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            Toast.makeText(this, "Error verifying signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String signText(String text) throws Exception {
        // Use the standard Dogecoin message signing format
        // This matches the dogecoin-signature-tool implementation
        String messagePrefix = "Dogecoin Signed Message:\n"; // No \x19 byte prefix
        byte[] messageBytes = text.getBytes(StandardCharsets.UTF_8);


        // Create the message hash using the standard Bitcoin message signing format
        Sha256Hash messageHash = createStandardMessageHash(messagePrefix, messageBytes);

        // Sign the hash
        ECKey.ECDSASignature signature = signingKey.sign(messageHash);

        // Create raw signature format (65 bytes: 1 byte recovery ID + 32 bytes r + 32 bytes s)
        // This matches the format expected by bitcoinjs-message and dogecoin-signature-tool
        byte[] rawSignature = createRawSignature(signature, messageHash);
        
        String base64Signature = android.util.Base64.encodeToString(rawSignature, android.util.Base64.NO_WRAP);
        
        return base64Signature;
    }
    
    private byte[] createRawSignature(ECKey.ECDSASignature signature, Sha256Hash messageHash) {
        // Create raw signature format (65 bytes: 1 byte recovery ID + 32 bytes r + 32 bytes s)
        // This matches the working example provided
        
        // Find the correct recovery ID by trying all possibilities
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            try {
                ECKey recoveredKey = ECKey.recoverFromSignature(i, signature, messageHash, signingKey.isCompressed());
                if (recoveredKey != null) {
                    // Compare full public key
                    if (java.util.Arrays.equals(recoveredKey.getPubKey(), signingKey.getPubKey())) {
                        recId = i;
                        break;
                    }
                }
            } catch (Exception e) {
                // Continue to next recovery ID
            }
        }
        
        if (recId == -1) {
            throw new IllegalStateException("Could not find recId");
        }
        
        // Build compact signature: 65 bytes [header(1) | R(32) | S(32)]
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
            // Create the message hash using the Dogecoin message signing format
            // This matches the working example provided
            byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);

            // Dogecoin format: varint(prefixLen) + prefix + varint(msgLen) + message
            byte[] prefixLen = varInt(prefixBytes.length);
            byte[] msgLen = varInt(message.length);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(prefixLen);
            baos.write(prefixBytes);
            baos.write(msgLen);
            baos.write(message);

            byte[] fullMessage = baos.toByteArray();

            // Double SHA256 of the full message
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
    
    private void writeCompactSize(ByteArrayOutputStream baos, long value) throws IOException {
        if (value < 253) {
            baos.write((int) value);
        } else if (value <= 0xFFFF) {
            baos.write(253);
            baos.write((int) (value & 0xFF));
            baos.write((int) ((value >> 8) & 0xFF));
        } else if (value <= 0xFFFFFFFFL) {
            baos.write(254);
            baos.write((int) (value & 0xFF));
            baos.write((int) ((value >> 8) & 0xFF));
            baos.write((int) ((value >> 16) & 0xFF));
            baos.write((int) ((value >> 24) & 0xFF));
        } else {
            baos.write(255);
            baos.write((int) (value & 0xFF));
            baos.write((int) ((value >> 8) & 0xFF));
            baos.write((int) ((value >> 16) & 0xFF));
            baos.write((int) ((value >> 24) & 0xFF));
            baos.write((int) ((value >> 32) & 0xFF));
            baos.write((int) ((value >> 40) & 0xFF));
            baos.write((int) ((value >> 48) & 0xFF));
            baos.write((int) ((value >> 56) & 0xFF));
        }
    }

    private boolean verifyTextSignature(String text, String signatureInput, String address) throws Exception {
        try {
            // Use the standard Dogecoin message signing format
            String messagePrefix = "Dogecoin Signed Message:\n"; // No \x19 byte prefix
            byte[] messageBytes = text.getBytes(StandardCharsets.UTF_8);
            
            // Create the message hash using the standard format
            Sha256Hash messageHash = createStandardMessageHash(messagePrefix, messageBytes);
        
            // Decode signature - try Base64 first, then hex
            byte[] signatureBytes;
            ECKey.ECDSASignature signature;
            
            try {
                // Try Base64 first - add padding if needed
                String paddedSignature = signatureInput;
                while (paddedSignature.length() % 4 != 0) {
                    paddedSignature += "=";
                }
                
                signatureBytes = android.util.Base64.decode(paddedSignature, android.util.Base64.DEFAULT);
                
                // Try raw signature format first (65 bytes)
                if (signatureBytes.length == 65) {
                    byte recoveryId = signatureBytes[0];
                    byte[] rBytes = new byte[32];
                    byte[] sBytes = new byte[32];
                    System.arraycopy(signatureBytes, 1, rBytes, 0, 32);
                    System.arraycopy(signatureBytes, 33, sBytes, 0, 32);
                    
                    signature = new ECKey.ECDSASignature(
                        new java.math.BigInteger(1, rBytes),
                        new java.math.BigInteger(1, sBytes)
                    );
                } else {
                    // Try DER format
                    signature = ECKey.ECDSASignature.decodeFromDER(signatureBytes);
                }
            } catch (Exception e1) {
                try {
                    // Try hex
                    signatureBytes = Utils.HEX.decode(signatureInput);
                    if (signatureBytes.length == 65) {
                        // Raw format
                        byte recoveryId = signatureBytes[0];
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
                } catch (Exception e2) {
                    log.error("Could not decode signature as Base64 or hex", e2);
                    return false;
                }
            }
            
            // Parse the address
            Address addressObj = LegacyAddress.fromBase58(Constants.NETWORK_PARAMETERS, address);
            
            // Try to recover the public key from the signature with all possible recovery IDs
            ECKey recoveredKey = null;
            Address recoveredAddress = null;
            boolean addressMatches = false;
            
            // Try recovery IDs 0-3 for both compressed and uncompressed keys
            for (int recId = 0; recId < 4; recId++) {
                try {
                    recoveredKey = ECKey.recoverFromSignature(recId, signature, messageHash, true);
                    if (recoveredKey != null) {
                        // Check if the recovered public key matches the address
                        recoveredAddress = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, recoveredKey);
                        addressMatches = recoveredAddress.toString().equals(address);
                        
                        if (addressMatches) {
                            break; // Found the correct recovery ID
                        }
                    }
                } catch (Exception e) {
                    // Continue to next recovery ID
                }
                
                // Also try uncompressed
                try {
                    recoveredKey = ECKey.recoverFromSignature(recId, signature, messageHash, false);
                    if (recoveredKey != null) {
                        // Check if the recovered public key matches the address
                        recoveredAddress = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, recoveredKey);
                        addressMatches = recoveredAddress.toString().equals(address);
                        
                        if (addressMatches) {
                            break; // Found the correct recovery ID
                        }
                    }
                } catch (Exception e) {
                    // Continue to next recovery ID
                }
            }
            
            if (recoveredKey == null || !addressMatches) {
                return false;
            }
            
            // Verify signature with recovered public key
            boolean signatureValid = ECKey.verify(messageHash.getBytes(), signature, recoveredKey.getPubKey());
            
            return signatureValid;
            
        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return false;
        }
    }

    private String readFileContent(Uri uri) throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
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
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void showResults(String signature, String address) {
        textSignatureResult.setText(signature);
        textAddressResult.setText(address);
        
        if (isFileSignature) {
            // File signature: Show file hash, hide signed text
            if (currentFileHash != null) {
                textFileHashLabel.setVisibility(View.VISIBLE);
                textFileHashResult.setVisibility(View.VISIBLE);
                textFileHashResult.setText(currentFileHash);
            } else {
                textFileHashLabel.setVisibility(View.GONE);
                textFileHashResult.setVisibility(View.GONE);
                textFileHashResult.setText("");
            }
            textSignedTextLabel.setVisibility(View.GONE);
            textSignedTextResult.setVisibility(View.GONE);
            textSignedTextResult.setText("");
        } else {
            // Text signature: Show signed text, hide file hash
            if (currentSignedContent != null) {
                textSignedTextLabel.setVisibility(View.VISIBLE);
                textSignedTextResult.setVisibility(View.VISIBLE);
                textSignedTextResult.setText(currentSignedContent);
            } else {
                textSignedTextLabel.setVisibility(View.GONE);
                textSignedTextResult.setVisibility(View.GONE);
                textSignedTextResult.setText("");
            }
            textFileHashLabel.setVisibility(View.GONE);
            textFileHashResult.setVisibility(View.GONE);
            textFileHashResult.setText("");
        }
        
        panelResults.setVisibility(View.VISIBLE);
        
        Toast.makeText(this, getString(R.string.digital_signature_signature_generated), Toast.LENGTH_SHORT).show();
    }

    private void copySignature() {
        String signature = textSignatureResult.getText().toString();
        if (!TextUtils.isEmpty(signature)) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Signature", signature);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Signature copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSignature() {
        String signature = textSignatureResult.getText().toString();
        String address = textAddressResult.getText().toString();
        
        if (!TextUtils.isEmpty(signature)) {
            String shareText;
            
            if (isFileSignature) {
                // For file signatures, show File Hash, Digital Signature, and Doge Address
                String fileHash = currentFileHash != null ? currentFileHash : "Unknown";
                shareText = "File Hash (SHA256): " + fileHash + "\n\nDigital Signature:\n" + signature + "\n\nDoge Address: " + address;
            } else {
                // For text signatures, show Message, Digital Signature, and Doge Address
                String message = currentSignedContent != null ? currentSignedContent : editTextToSign.getText().toString();
                shareText = "Message: " + message + "\n\nDigital Signature:\n" + signature + "\n\nDoge Address: " + address;
            }
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(shareIntent, "Share Signature"));
        }
    }

    private void saveSignatureToDatabase() {
        String signature = textSignatureResult.getText().toString();
        String address = textAddressResult.getText().toString();
        String tag = editTag.getText().toString().trim();
        
        if (TextUtils.isEmpty(signature) || TextUtils.isEmpty(address)) {
            Toast.makeText(this, "No signature to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            DigitalSignature sigRecord = new DigitalSignature();
            sigRecord.setSignature(signature);
            sigRecord.setAddress(address);
            sigRecord.setTag(TextUtils.isEmpty(tag) ? null : tag);
            
            if (isFileSignature) {
                // File or photo signature
                if (selectedFilePath != null && selectedFilePath.contains("SIGNED_")) {
                    sigRecord.setType("photo");
                    sigRecord.setContent(selectedFilePath);
                } else {
                    sigRecord.setType("file");
                    sigRecord.setContent(selectedFilePath);
                }
                sigRecord.setFileHash(currentFileHash);
            } else {
                // Text signature
                sigRecord.setType("text");
                sigRecord.setContent(currentSignedContent);
                sigRecord.setFileHash(null);
            }
            
            sigRecord.setTimestamp(System.currentTimeMillis());
            
            long id = database.digitalSignatureDao().insertSignature(sigRecord);
            if (id > 0) {
                Toast.makeText(this, "Signature saved successfully", Toast.LENGTH_SHORT).show();
                // Clear tag field after saving
                editTag.setText("");
            } else {
                Toast.makeText(this, "Error saving signature", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            log.error("Error saving signature to database", e);
            Toast.makeText(this, "Error saving signature: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void clearResults() {
        textSignatureResult.setText("");
        textAddressResult.setText("");
        textSignedTextLabel.setVisibility(View.GONE);
        textSignedTextResult.setVisibility(View.GONE);
        textSignedTextResult.setText("");
        textFileHashLabel.setVisibility(View.GONE);
        textFileHashResult.setVisibility(View.GONE);
        textFileHashResult.setText("");
        panelResults.setVisibility(View.GONE);
        
        // Clear input fields
        editTextToSign.setText("");
        editOriginalText.setText("");
        editSignature.setText("");
        editVerificationAddress.setText("");
        editTag.setText("");
        textSelectedFile.setText("No file selected");
        btnSignSelectedFile.setEnabled(false);
        selectedFileUri = null;
        selectedFilePath = null;
        currentPhotoPath = null;
        
        // Clear signature tracking
        currentSignedContent = null;
        isFileSignature = false;
        currentFileHash = null;
    }
    
    private void ensureSignedFolderExists() {
        try {
            File picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (picturesDir == null) {
                log.warn("External pictures directory not available");
                return;
            }
            
            File signedFolder = new File(picturesDir, "signed");
            if (!signedFolder.exists()) {
                boolean created = signedFolder.mkdirs();
                log.info("Created signed folder on startup: {}, exists now: {}", created, signedFolder.exists());
            } else {
                log.info("Signed folder already exists on startup: {}", signedFolder.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Error ensuring signed folder exists", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Securely clear the signing key from memory
        if (signingKey != null) {
            // Clear the private key bytes
            try {
                byte[] privateKeyBytes = signingKey.getPrivKeyBytes();
                if (privateKeyBytes != null) {
                    SecureMemory.clear(privateKeyBytes);
                }
            } catch (Exception e) {
                // Ignore if we can't access the private key bytes
            }
            signingKey = null;
        }
        
        // Clear any other sensitive data
        clearResults();
    }
}
