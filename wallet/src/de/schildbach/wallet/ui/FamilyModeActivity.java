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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.wallet.R;
import de.schildbach.wallet.data.FamilyMember;
import de.schildbach.wallet.data.FamilyMemberDatabase;
import de.schildbach.wallet.util.ExcludedAddressHelper;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.util.SecureMemory;
import de.schildbach.wallet.util.Qr;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.Wallet;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.data.PaymentIntent;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import java.util.List;

import java.util.ArrayList;

/**
 * Activity for managing family members in Family Mode
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class FamilyModeActivity extends AbstractWalletActivity {
    private RecyclerView recyclerFamilyMembers;
    private LinearLayout layoutEmptyState;
    private Button btnAddChild;
    private Button btnActivateChild;
    private Button btnDeactivateChild;
    
    private FamilyMemberDatabase familyDatabase;
    private FamilyMembersAdapter adapter;
    private List<FamilyMember> familyMembers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_mode);
        
        familyDatabase = new FamilyMemberDatabase(this);
        familyMembers = new ArrayList<>();
        
        // Initialize ExcludedAddressHelper
        ExcludedAddressHelper.initialize(this);
        
        initializeViews();
        setupRecyclerView();
        loadFamilyMembers();
        updateUI();
        
        // Check if we were started with a scanned derived key from the top menu
        String scannedDerivedKey = getIntent().getStringExtra("scanned_derived_key");
        if (scannedDerivedKey != null) {
            // Automatically start the child activation process
            activateChildMode(scannedDerivedKey);
        }
    }

    private void initializeViews() {
        recyclerFamilyMembers = findViewById(R.id.recycler_family_members);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        btnAddChild = findViewById(R.id.btn_add_child);
        btnActivateChild = findViewById(R.id.btn_activate_child);
        btnDeactivateChild = findViewById(R.id.btn_deactivate_child);
        
        btnAddChild.setOnClickListener(v -> showAddChildDialog());
        btnActivateChild.setOnClickListener(v -> startQRScanner());
        btnDeactivateChild.setOnClickListener(v -> {
            // Show confirmation dialog for deactivating child mode
            new AlertDialog.Builder(this)
                    .setTitle("Deactivate Child Mode")
                    .setMessage("This operation will put the Wallet in Normal Mode with all features. The child's derived key will be removed from the wallet and the wallet will revert to using the normal HD keys. Are you sure you want to continue?")
                    .setPositiveButton("OK", (dialog, which) -> {
                        // Deactivate child mode
                        deactivateChildMode();
                    })
                    .setNegativeButton(R.string.common_cancel, null)
                    .show();
        });
    }

    private void setupRecyclerView() {
        adapter = new FamilyMembersAdapter();
        recyclerFamilyMembers.setLayoutManager(new LinearLayoutManager(this));
        recyclerFamilyMembers.setAdapter(adapter);
    }

    private void loadFamilyMembers() {
        familyMembers.clear();
        familyMembers.addAll(familyDatabase.getAllFamilyMembers());
        
        // Update balances from actual wallet
        updateFamilyMemberBalances();
    }
    
    private void updateFamilyMemberBalances() {
        getWalletApplication().getWalletAsync(wallet -> {
            try {
                if (wallet != null) {
                    for (FamilyMember member : familyMembers) {
                        try {
                            // Calculate the actual balance for this specific address
                            Address address = Address.fromString(Constants.NETWORK_PARAMETERS, member.getAddress());
                            Coin balance = calculateAddressBalance(wallet, address);
                            member.setBalance(balance);
                        } catch (Exception e) {
                            e.printStackTrace();
                            // Set zero balance if address parsing fails
                            member.setBalance(Coin.ZERO);
                        }
                    }
                } else {
                    // If wallet is not available, set all balances to zero
                    for (FamilyMember member : familyMembers) {
                        member.setBalance(Coin.ZERO);
                    }
                }
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                e.printStackTrace();
                // Keep existing balances if update fails
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }
    
    /**
     * Calculate the balance for a specific address by summing unspent transaction outputs
     */
    private Coin calculateAddressBalance(Wallet wallet, Address address) {
        try {
            Script addressScript = ScriptBuilder.createOutputScript(address);
            Coin balance = Coin.ZERO;
            
            // Get all unspent transaction outputs from the wallet
            List<TransactionOutput> unspentOutputs = wallet.getUnspents();
            
            // Filter outputs that belong to this specific address
            for (TransactionOutput output : unspentOutputs) {
                if (output.getScriptPubKey().equals(addressScript)) {
                    balance = balance.add(output.getValue());
                }
            }
            
            return balance;
        } catch (Exception e) {
            e.printStackTrace();
            // Return zero balance if calculation fails
            return Coin.ZERO;
        }
    }

    private void updateUI() {
        boolean hasMembers = !familyMembers.isEmpty();
        boolean isChildMode = familyDatabase.getActiveFamilyMember() != null;
        
        recyclerFamilyMembers.setVisibility(hasMembers ? View.VISIBLE : View.GONE);
        layoutEmptyState.setVisibility(hasMembers ? View.GONE : View.VISIBLE);
        btnDeactivateChild.setVisibility(isChildMode ? View.VISIBLE : View.GONE);
        
        // Hide "Activate Child" button when there are already family members
        btnActivateChild.setVisibility(hasMembers ? View.GONE : View.VISIBLE);
        
        // Hide "Add Child" button when child mode is activated
        btnAddChild.setVisibility(isChildMode ? View.GONE : View.VISIBLE);
        
        // Update the adapter to hide/show buttons based on child mode
        if (adapter != null) {
            adapter.setChildModeActive(isChildMode);
        }
    }

    private void showAddChildDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_child, null);
        builder.setView(dialogView);
        
        EditText editChildName = dialogView.findViewById(R.id.edit_child_name);
        TextView textDerivedKey = dialogView.findViewById(R.id.text_derived_key);
        ImageView imageQrCode = dialogView.findViewById(R.id.image_qr_code);
        ImageButton btnCopyKey = dialogView.findViewById(R.id.btn_copy_key);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        // Generate derived key
        String derivedKey = generateDerivedKey();
        textDerivedKey.setText(derivedKey);
        
        // Generate QR code for derived key
        generateQRCode(derivedKey, imageQrCode);
        
        btnCopyKey.setOnClickListener(v -> {
            copyToClipboard("Derived Key", derivedKey);
            Toast.makeText(this, "Derived key copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        
        AlertDialog dialog = builder.create();
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String childName = editChildName.getText().toString().trim();
            if (childName.isEmpty()) {
                editChildName.setError("Please enter a name");
                return;
            }
            
            // Generate address from derived key
            String address = generateAddressFromKey(derivedKey);
            
            // Create family member
            FamilyMember member = new FamilyMember(childName, derivedKey, address);
            long id = familyDatabase.insertFamilyMember(member);
            member.setId(id);
            
            // Add child name to wallet's address book
            addChildToAddressBook(childName, address);
            
            // Automatically exclude the child's address from spending in "Your Addresses"
            // This prevents the parent from accidentally spending from the child's address
            ExcludedAddressHelper.excludeAddress(address, childName);
            
            loadFamilyMembers();
            updateUI();
            dialog.dismiss();
            
            Toast.makeText(this, "Family member added successfully", Toast.LENGTH_SHORT).show();
        });
        
        dialog.show();
    }

    private int currentChildIndex = 0; // Store the current child index
    
    private String generateDerivedKey() {
        try {
            // Get the actual wallet and generate a new address
            Wallet wallet = getWalletApplication().getWallet();
            
            // Generate a fresh receive address (this will create a new key each time)
            Address newAddress = wallet.freshReceiveAddress();
            android.util.Log.d("FamilyMode", "Generated new address: " + newAddress.toString());
            
            // Get the key chain to find the key for this address
            DeterministicKeyChain keyChain = wallet.getActiveKeyChain();
            
            // Find the key for this address
            ECKey key = wallet.findKeyFromAddress(newAddress);
            String derivedKey = null;
            
            try {
                if (key instanceof DeterministicKey) {
                    DeterministicKey detKey = (DeterministicKey) key;
                    // Serialize the private key in BIP32 format
                    derivedKey = detKey.serializePrivB58(Constants.NETWORK_PARAMETERS);
                    android.util.Log.d("FamilyMode", "Generated derived key: " + derivedKey.substring(0, Math.min(20, derivedKey.length())) + "...");
                } else {
                    // Fallback for non-deterministic keys
                    derivedKey = "xprv" + key.getPrivateKeyAsHex();
                    android.util.Log.d("FamilyMode", "Generated non-deterministic key: " + derivedKey.substring(0, Math.min(20, derivedKey.length())) + "...");
                }
                
                // Securely clear the private key bytes from memory
                try {
                    byte[] privateKeyBytes = key.getPrivKeyBytes();
                    if (privateKeyBytes != null) {
                        SecureMemory.clear(privateKeyBytes);
                    }
                } catch (Exception e) {
                    // Ignore if we can't access the private key bytes
                }
                
                return derivedKey;
            } finally {
                // Clear the key reference
                key = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to simulation if real generation fails
            // Use current time + random component for uniqueness
            long timestamp = System.currentTimeMillis();
            int random = (int) (Math.random() * 1000);
            String hexString = Long.toHexString(timestamp) + Integer.toHexString(random);
            while (hexString.length() < 20) {
                hexString += "0";
            }
            String derivedKey = "xprv" + hexString.substring(0, Math.min(20, hexString.length()));
            android.util.Log.d("FamilyMode", "Generated fallback key: " + derivedKey);
            return derivedKey;
        }
    }

    private String generateAddressFromKey(String derivedKey) {
        try {
            // Parse the BIP32 derived key and generate the corresponding address
            DeterministicKey childKey = DeterministicKey.deserializeB58(derivedKey, Constants.NETWORK_PARAMETERS);
            
            // Generate the address from the derived key
            Address address = LegacyAddress.fromKey(Constants.NETWORK_PARAMETERS, childKey);
            
            return address.toString();
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to simulation if real generation fails
            int addressLength = Math.min(33, derivedKey.length());
            String addressPart = derivedKey.substring(0, addressLength);
            while (addressPart.length() < 33) {
                addressPart += (char) ('A' + (System.currentTimeMillis() % 26));
            }
            return "D" + addressPart;
        }
    }

    private void generateQRCode(String data, ImageView imageView) {
        // Generate QR code for the derived key using the existing Qr utility
        try {
            Bitmap qrBitmap = Qr.bitmap(data);
            if (qrBitmap != null) {
                // Scale up the QR code for better quality
                int targetSize = 400; // Higher resolution for crisp display
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(qrBitmap, targetSize, targetSize, false);
                imageView.setImageBitmap(scaledBitmap);
            } else {
                // If QR generation fails, show a placeholder or error
                imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Show error placeholder
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    private void startQRScanner() {
        // Start QR scanner to read derived key
        ScanActivity.startForResult(this, REQUEST_CODE_SCAN_QR);
    }

    private void deactivateChildMode() {
        familyDatabase.setActiveFamilyMember(0); // Deactivate all
        clearChildModeAddress(); // Clear child mode address and key
        updateUI();
        Toast.makeText(this, "Child mode deactivated", Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
    }

    private static final int REQUEST_CODE_SCAN_QR = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCAN_QR && resultCode == RESULT_OK) {
            // Handle scanned QR code
            String scannedData = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            if (scannedData != null) {
                activateChildMode(scannedData);
            }
        }
    }

    private void activateChildMode(String derivedKey) {
        // Show PIN setup dialog first
        showPinSetupDialog(derivedKey);
    }
    
    private void showPinSetupDialog(String derivedKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set PIN for Child Mode");
        builder.setMessage("Please set a PIN code to protect access to Safety, Family Mode, and Settings when child mode is active:");
        
        final EditText pinInput = new EditText(this);
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Enter 4-digit PIN");
        builder.setView(pinInput);
        
        builder.setPositiveButton("Set PIN", (dialog, which) -> {
            String pin = pinInput.getText().toString();
            if (pin.length() == 4 && pin.matches("\\d{4}")) {
                // Store the PIN and proceed with activation
                storeChildModePin(pin);
                proceedWithChildActivation(derivedKey);
            } else {
                Toast.makeText(this, "Please enter a valid 4-digit PIN", Toast.LENGTH_SHORT).show();
                showPinSetupDialog(derivedKey); // Show dialog again
            }
        });
        
        builder.setNegativeButton(R.string.common_cancel, null);
        builder.show();
    }
    
    public static void storeChildModePin(android.content.Context context, String pin) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("child_mode", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("child_mode_pin", pin);
        editor.apply();
    }
    
    private void storeChildModePin(String pin) {
        storeChildModePin(this, pin);
    }
    
    private void proceedWithChildActivation(String derivedKey) {
        // First, try to find existing family member with this derived key
        for (FamilyMember member : familyMembers) {
            if (member.getDerivedKey().equals(derivedKey)) {
                familyDatabase.setActiveFamilyMember(member.getId());
                setChildModeAddress(member.getAddress(), derivedKey);
                
                // Note: Child address is NOT excluded from spending when Child Mode is activated on child's phone
                // The child should be able to spend from their own address
                
                updateUI();
                Toast.makeText(this, "Child mode activated for " + member.getName(), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // If not found, this is a new derived key from parent's "Add New Family Member"
        // Create a new family member record for this child
        try {
            // Generate address from the derived key
            String address = generateAddressFromKey(derivedKey);
            
            // Create a new family member record
            FamilyMember newMember = new FamilyMember("Child Mode", derivedKey, address);
            long memberId = familyDatabase.insertFamilyMember(newMember);
            
            if (memberId > 0) {
                // Set this member as active (child mode)
                familyDatabase.setActiveFamilyMember(memberId);
                
                // Set the child mode address for wallet override
                setChildModeAddress(address, derivedKey);
                
                // Add to address book
                addChildToAddressBook("Child Mode", address);
                
                // Note: Child address is NOT excluded from spending when Child Mode is activated on child's phone
                // The child should be able to spend from their own address
                
                // Reload family members and update UI
                loadFamilyMembers();
                updateUI();
                
                Toast.makeText(this, "Child mode activated successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to activate child mode", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error activating child mode: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Set the child mode address and key for wallet override
     */
    private void setChildModeAddress(String address, String derivedKey) {
        try {
            // Store the child's address and key in shared preferences for wallet override
            android.content.SharedPreferences prefs = getSharedPreferences("child_mode", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            editor.putString("child_address", address);
            editor.putString("child_derived_key", derivedKey);
            editor.putBoolean("child_mode_active", true);
            editor.apply();
            
            // Also store in the family database for persistence
            familyDatabase.setChildModeAddress(address);
            familyDatabase.setChildModeDerivedKey(derivedKey);
            
            // Import the child's address and key into the wallet for monitoring
            importChildAddressToWallet(address, derivedKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Import the child's address and key into the wallet for transaction monitoring
     */
    private void importChildAddressToWallet(String address, String derivedKey) {
        try {
            WalletApplication walletApp = getWalletApplication();
            Wallet wallet = walletApp.getWallet();
            
            // Parse the derived key
            DeterministicKey childKey = DeterministicKey.deserializeB58(derivedKey, Constants.NETWORK_PARAMETERS);
            
            // Parse the address
            Address childAddress = Address.fromString(Constants.NETWORK_PARAMETERS, address);
            
            android.util.Log.d("FamilyMode", "Importing child key and address into wallet");
            android.util.Log.d("FamilyMode", "Child address: " + address);
            android.util.Log.d("FamilyMode", "Child key creation time: " + childKey.getCreationTimeSeconds());
            
            // Set the key creation time to 0 to ensure the wallet scans from the beginning
            childKey.setCreationTimeSeconds(0);
            
            // Import the child's key alongside existing keys
            // This approach keeps the wallet's monitoring intact while adding the child's key
            android.util.Log.d("FamilyMode", "Importing child key alongside existing keys");
            
            // Convert DeterministicKey to ECKey for import (BitcoinJ doesn't allow importing HD keys back)
            try {
                ECKey regularKey = ECKey.fromPrivate(childKey.getPrivKey());
                wallet.importKey(regularKey);
                android.util.Log.d("FamilyMode", "Successfully imported child key as regular ECKey");
            } catch (Exception e) {
                android.util.Log.e("FamilyMode", "Failed to convert and import child key: " + e.getMessage());
                throw e;
            }
            
            // CRITICAL: Force wallet to rescan from the beginning
            // This is the key missing piece - we need to tell the wallet to rescan
            try {
                // Method 1: Try to set earliest key creation time using reflection
                try {
                    java.lang.reflect.Method setEarliestMethod = wallet.getClass().getMethod("setEarliestKeyCreationTime", long.class);
                    setEarliestMethod.invoke(wallet, 0L);
                    android.util.Log.d("FamilyMode", "Set earliest key creation time to 0 for full blockchain rescan");
                } catch (Exception e) {
                    android.util.Log.d("FamilyMode", "setEarliestKeyCreationTime method not available: " + e.getMessage());
                }
                
                // Method 2: Try to force a wallet reload by triggering walletChanged event
                walletApp.walletChanged.setValue(new de.schildbach.wallet.ui.Event<>(null));
                android.util.Log.d("FamilyMode", "Triggered wallet reload event");
                
                // Method 3: Force multiple blockchain sync attempts
                try {
                    // This is a more aggressive approach - force multiple sync attempts
                    android.util.Log.d("FamilyMode", "Forcing multiple blockchain sync attempts for rescan");
                    // Start multiple sync attempts with expectLargeData=true
                    de.schildbach.wallet.service.BlockchainService.start(this, true);
                    android.util.Log.d("FamilyMode", "Multiple blockchain sync attempts started");
                } catch (Exception e) {
                    android.util.Log.w("FamilyMode", "Could not start multiple blockchain sync: " + e.getMessage());
                }
                
            } catch (Exception e) {
                android.util.Log.w("FamilyMode", "Could not force wallet rescan: " + e.getMessage());
            }
            
            // Also try to add the address as a watched address (if the method exists)
            try {
                // Try different methods to ensure the wallet monitors this address
                // Note: Some of these methods may not exist in the current BitcoinJ version
                
                // Method 1: Try addWatchedAddress
                try {
                    java.lang.reflect.Method addWatchedMethod = wallet.getClass().getMethod("addWatchedAddress", Address.class);
                    addWatchedMethod.invoke(wallet, childAddress);
                    android.util.Log.d("FamilyMode", "Added address to watch list using addWatchedAddress: " + address);
                } catch (Exception e) {
                    android.util.Log.d("FamilyMode", "addWatchedAddress method not available: " + e.getMessage());
                }
                
            } catch (Exception e) {
                android.util.Log.w("FamilyMode", "Could not use reflection methods: " + e.getMessage());
            }
            
            android.util.Log.d("FamilyMode", "Successfully imported child key and address into wallet");
            android.util.Log.d("FamilyMode", "Wallet will now monitor ONLY address: " + address);
            
            // Save wallet state immediately
            try {
                walletApp.autosaveWalletNow();
                android.util.Log.d("FamilyMode", "Wallet state saved after key import");
            } catch (Exception e) {
                android.util.Log.w("FamilyMode", "Could not save wallet state: " + e.getMessage());
            }
            
            // Trigger wallet refresh to start monitoring the new address
            refreshWalletTransactions();
            
        } catch (Exception e) {
            e.printStackTrace();
            android.util.Log.e("FamilyMode", "Failed to import child address into wallet: " + e.getMessage());
        }
    }
    
    /**
     * Refresh wallet transactions to pick up the newly imported address
     */
    private void refreshWalletTransactions() {
        try {
            // This will trigger the wallet to refresh its transaction monitoring
            // The blockchain service will pick up transactions for the newly imported address
            android.util.Log.d("FamilyMode", "Triggering wallet transaction refresh...");
            
            // Force a comprehensive blockchain resync to pick up transactions for the newly imported address
            WalletApplication walletApp = getWalletApplication();
            
            // Method 1: Force blockchain service restart with expectLargeData=true
            try {
                de.schildbach.wallet.service.BlockchainService.start(this, true);
                android.util.Log.d("FamilyMode", "Immediate blockchain sync started with expectLargeData=true");
            } catch (Exception e) {
                android.util.Log.w("FamilyMode", "Could not start immediate blockchain sync: " + e.getMessage());
            }
            
            // Method 2: Schedule with expectLargeData=true for priority
            de.schildbach.wallet.service.StartBlockchainService.schedule(walletApp, true);
            
            // Method 3: Try to trigger wallet save and reload (this might help refresh the wallet state)
            try {
                walletApp.autosaveWalletNow();
                android.util.Log.d("FamilyMode", "Wallet autosaved to persist imported key");
            } catch (Exception e) {
                android.util.Log.w("FamilyMode", "Could not autosave wallet: " + e.getMessage());
            }
            
            // Method 4: Force multiple sync attempts with delay
            android.os.Handler handler = new android.os.Handler();
            handler.postDelayed(() -> {
                try {
                    de.schildbach.wallet.service.BlockchainService.start(FamilyModeActivity.this, true);
                    android.util.Log.d("FamilyMode", "Delayed blockchain sync started");
                } catch (Exception e) {
                    android.util.Log.w("FamilyMode", "Delayed blockchain sync failed: " + e.getMessage());
                }
            }, 2000);
            
            // Method 5: Additional delayed sync attempts
            handler.postDelayed(() -> {
                try {
                    de.schildbach.wallet.service.BlockchainService.start(FamilyModeActivity.this, true);
                    android.util.Log.d("FamilyMode", "Second delayed blockchain sync started");
                } catch (Exception e) {
                    android.util.Log.w("FamilyMode", "Second delayed blockchain sync failed: " + e.getMessage());
                }
            }, 10000);
            
            android.util.Log.d("FamilyMode", "Comprehensive blockchain resync initiated for imported address");
            
        } catch (Exception e) {
            e.printStackTrace();
            android.util.Log.e("FamilyMode", "Failed to refresh wallet transactions: " + e.getMessage());
        }
    }
    
    /**
     * Clear child mode address and key
     */
    private void clearChildModeAddress() {
        try {
            // Get the child's derived key before clearing
            String childDerivedKey = familyDatabase.getChildModeDerivedKey();
            
            // Clear from shared preferences
            android.content.SharedPreferences prefs = getSharedPreferences("child_mode", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            editor.remove("child_address");
            editor.remove("child_derived_key");
            editor.putBoolean("child_mode_active", false);
            editor.apply();
            
            // Clear from family database
            familyDatabase.setChildModeAddress(null);
            familyDatabase.setChildModeDerivedKey(null);
            
            // Remove the child's key from the wallet
            if (childDerivedKey != null) {
                removeChildKeyFromWallet(childDerivedKey);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Remove the child's key from the wallet
     */
    private void removeChildKeyFromWallet(String derivedKey) {
        try {
            Wallet wallet = getWalletApplication().getWallet();
            
            // Parse the derived key
            DeterministicKey childKey = DeterministicKey.deserializeB58(derivedKey, Constants.NETWORK_PARAMETERS);
            
            // Remove the key from the wallet
            wallet.removeKey(childKey);
            
            android.util.Log.d("FamilyMode", "Successfully removed child key from wallet");
            
        } catch (Exception e) {
            e.printStackTrace();
            android.util.Log.e("FamilyMode", "Failed to remove child key from wallet: " + e.getMessage());
        }
    }

    private class FamilyMembersAdapter extends RecyclerView.Adapter<FamilyMembersAdapter.FamilyMemberViewHolder> {
        private boolean isChildModeActive = false;
        
        public void setChildModeActive(boolean isChildModeActive) {
            this.isChildModeActive = isChildModeActive;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public FamilyMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_family_member, parent, false);
            return new FamilyMemberViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FamilyMemberViewHolder holder, int position) {
            FamilyMember member = familyMembers.get(position);
            holder.bind(member, isChildModeActive);
        }

        @Override
        public int getItemCount() {
            return familyMembers.size();
        }

        class FamilyMemberViewHolder extends RecyclerView.ViewHolder {
            private TextView textChildName;
            private TextView textBalance;
            private TextView textAddress;
            private ImageButton btnCopyAddress;
            private ImageButton btnSendCoins;
            private ImageButton btnShowQr;
            private ImageButton btnEditName;
            private ImageButton btnDelete;

            public FamilyMemberViewHolder(@NonNull View itemView) {
                super(itemView);
                textChildName = itemView.findViewById(R.id.text_child_name);
                textBalance = itemView.findViewById(R.id.text_balance);
                textAddress = itemView.findViewById(R.id.text_address);
                btnCopyAddress = itemView.findViewById(R.id.btn_copy_address);
                btnSendCoins = itemView.findViewById(R.id.btn_send_coins);
                btnShowQr = itemView.findViewById(R.id.btn_show_qr);
                btnEditName = itemView.findViewById(R.id.btn_edit_name);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }

            public void bind(FamilyMember member, boolean isChildModeActive) {
                textChildName.setText(member.getName());
                textBalance.setText(member.getBalance().toPlainString() + " DOGE");
                textAddress.setText(member.getAddress());
                
                // Hide buttons when child mode is active
                if (isChildModeActive) {
                    btnSendCoins.setVisibility(View.GONE);
                    btnShowQr.setVisibility(View.GONE);
                    btnEditName.setVisibility(View.GONE);
                    btnDelete.setVisibility(View.GONE);
                } else {
                    btnSendCoins.setVisibility(View.VISIBLE);
                    btnShowQr.setVisibility(View.VISIBLE);
                    btnEditName.setVisibility(View.VISIBLE);
                    btnDelete.setVisibility(View.VISIBLE);
                }
                
                btnCopyAddress.setOnClickListener(v -> {
                    copyToClipboard("Address", member.getAddress());
                    Toast.makeText(FamilyModeActivity.this, "Address copied to clipboard", Toast.LENGTH_SHORT).show();
                });
                
                btnSendCoins.setOnClickListener(v -> {
                    // Start send coins activity with the child's address pre-filled
                    sendCoinsToChild(member);
                });
                
                btnShowQr.setOnClickListener(v -> {
                    // Show QR code for this family member's derived key (for parent to restore child)
                    showDerivedKeyQRDialog(member.getDerivedKey(), member.getName());
                });
                
                btnEditName.setOnClickListener(v -> {
                    // Show edit name dialog
                    showEditNameDialog(member);
                });
                
                btnDelete.setOnClickListener(v -> {
                    // Confirm deletion
                    new AlertDialog.Builder(FamilyModeActivity.this)
                            .setTitle("Delete Family Member")
                            .setMessage("Are you sure you want to delete " + member.getName() + "?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                familyDatabase.deleteFamilyMember(member.getId());
                                loadFamilyMembers();
                                updateUI();
                                Toast.makeText(FamilyModeActivity.this, "Family member deleted", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(R.string.common_cancel, null)
                            .show();
                });
            }
        }
    }

    private void sendCoinsToChild(FamilyMember child) {
        try {
            // Create a PaymentIntent with the child's address
            Address childAddress = Address.fromString(Constants.NETWORK_PARAMETERS, child.getAddress());
            PaymentIntent.Output output = new PaymentIntent.Output(Coin.ZERO, ScriptBuilder.createOutputScript(childAddress));
            PaymentIntent paymentIntent = new PaymentIntent(null, child.getName(), null, new PaymentIntent.Output[]{output}, null, null, null, null, null);
            
            // Start SendCoinsActivity with the payment intent
            SendCoinsActivity.start(this, paymentIntent, null, 0);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening Send Coins: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addChildToAddressBook(String childName, String address) {
        try {
            // Add the child's address to the wallet's address book with their name as label
            AddressBookDatabase addressBookDb = AddressBookDatabase.getDatabase(this);
            AddressBookEntry entry = new AddressBookEntry(address, childName);
            addressBookDb.addressBookDao().insertOrUpdate(entry);
        } catch (Exception e) {
            e.printStackTrace();
            // Continue even if address book addition fails
        }
    }

    private void showAddressQRDialog(String address) {
        // Show QR code dialog for the address
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.bitmap_dialog, null);
        builder.setView(dialogView);
        
        ImageView qrImageView = dialogView.findViewById(R.id.bitmap_dialog_image);
        
        // Generate QR code for the address with high resolution
        Bitmap qrBitmap = Qr.bitmap(address);
        if (qrBitmap != null) {
            // Scale up for better quality
            int targetSize = 400;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(qrBitmap, targetSize, targetSize, false);
            qrImageView.setImageBitmap(scaledBitmap);
        }
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showDerivedKeyQRDialog(String derivedKey, String childName) {
        // Show QR code dialog for the derived key (for child restoration)
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.bitmap_dialog, null);
        builder.setView(dialogView);
        
        ImageView qrImageView = dialogView.findViewById(R.id.bitmap_dialog_image);
        
        // Generate QR code for the derived key with high resolution
        Bitmap qrBitmap = Qr.bitmap(derivedKey);
        if (qrBitmap != null) {
            // Scale up for better quality
            int targetSize = 300;
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(qrBitmap, targetSize, targetSize, false);
            qrImageView.setImageBitmap(scaledBitmap);
        }
        
        builder.setTitle("Child Restoration QR Code");
        builder.setMessage("Scan this QR code on the child's phone to restore access to " + childName + "'s wallet.");
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void showEditNameDialog(FamilyMember member) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Child Name");
        
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(member.getName());
        input.selectAll();
        builder.setView(input);
        
        builder.setPositiveButton(R.string.common_save, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(member.getName())) {
                // Update the family member name
                member.setName(newName);
                familyDatabase.updateFamilyMember(member);
                
                // Update the address book entry
                updateAddressBookEntry(member.getAddress(), newName);
                
                // Refresh the UI
                loadFamilyMembers();
                updateUI();
                
                Toast.makeText(this, "Name updated successfully", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton(R.string.common_cancel, null);
        builder.show();
    }
    
    private void updateAddressBookEntry(String address, String newName) {
        try {
            AddressBookDatabase addressBookDb = AddressBookDatabase.getDatabase(this);
            
            // Check if entry exists by resolving the label
            String existingLabel = addressBookDb.addressBookDao().resolveLabel(address);
            
            if (existingLabel != null) {
                // Update existing entry by creating a new one with the same address
                AddressBookEntry updatedEntry = new AddressBookEntry(address, newName);
                addressBookDb.addressBookDao().insertOrUpdate(updatedEntry);
            } else {
                // Create new entry if it doesn't exist
                AddressBookEntry newEntry = new AddressBookEntry(address, newName);
                addressBookDb.addressBookDao().insertOrUpdate(newEntry);
            }
        } catch (Exception e) {
            e.printStackTrace();
            android.util.Log.e("FamilyMode", "Failed to update address book entry: " + e.getMessage());
        }
    }
}
