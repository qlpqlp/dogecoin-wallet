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
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import de.schildbach.wallet.R;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.AuthenticationRequest;
import de.schildbach.wallet.data.AuthenticationRequestDao;
import de.schildbach.wallet.ui.scan.ScanActivity;
import de.schildbach.wallet.util.Qr;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activity for authentication by signing messages with Dogecoin addresses
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class AuthenticationActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationActivity.class);
    private static final int REQUEST_CODE_SCAN_MESSAGE = 1001;

    private WalletApplication application;
    private Wallet wallet;
    
    private Spinner addressSpinner;
    private Button btnGenerateNewAddress;
    private EditText editFilterAddress;
    private Button btnScanMessage;
    private TextView textSelectedAddress;
    private TextView textSignature;
    private ImageView qrCodeSignature;
    private Button btnCopySignature;
    private Button btnShareSignature;
    private ProgressBar progressBar;
    
    private CardView layoutSignatureResult;
    private CardView layoutStoredRequests;
    private ListView listStoredRequests;
    
    private Address selectedAddress;
    private String currentSignature;
    private List<Address> walletAddresses;
    private List<Address> filteredAddresses;
    private AddressAdapter addressAdapter;
    private ArrayAdapter<AuthenticationRequest> storedRequestsAdapter;
    
    private AddressBookDatabase database;
    private AuthenticationRequestDao authRequestDao;
    
    // Inner class for address adapter with labels
    private class AddressAdapter extends ArrayAdapter<Address> implements Filterable {
        private List<Address> originalAddresses;
        private List<Address> filteredAddresses;
        private AddressFilter filter;
        
        public AddressAdapter(Context context, List<Address> addresses) {
            super(context, android.R.layout.simple_spinner_item, addresses);
            this.originalAddresses = new ArrayList<>(addresses);
            this.filteredAddresses = new ArrayList<>(addresses);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }
        
        @Override
        public int getCount() {
            return filteredAddresses.size();
        }
        
        @Override
        public Address getItem(int position) {
            return filteredAddresses.get(position);
        }
        
        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView textView = (TextView) view;
            Address address = getItem(position);
            try {
                String label = database.addressBookDao().resolveLabel(address.toString());
                if (label != null && !label.trim().isEmpty()) {
                    textView.setText(label + " (" + address.toString() + ")");
                } else {
                    textView.setText(address.toString());
                }
            } catch (Exception e) {
                textView.setText(address.toString());
            }
            return view;
        }
        
        @Override
        public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            TextView textView = (TextView) view;
            Address address = getItem(position);
            try {
                String label = database.addressBookDao().resolveLabel(address.toString());
                if (label != null && !label.trim().isEmpty()) {
                    textView.setText(label + "\n" + address.toString());
                } else {
                    textView.setText(address.toString());
                }
            } catch (Exception e) {
                textView.setText(address.toString());
            }
            return view;
        }
        
        @Override
        public Filter getFilter() {
            if (filter == null) {
                filter = new AddressFilter();
            }
            return filter;
        }
        
        private class AddressFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<Address> filtered = new ArrayList<>();
                
                if (constraint == null || constraint.length() == 0) {
                    filtered.addAll(originalAddresses);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    for (Address address : originalAddresses) {
                        String addressStr = address.toString().toLowerCase();
                        String label = null;
                        try {
                            label = database.addressBookDao().resolveLabel(address.toString());
                        } catch (Exception e) {
                            // Ignore
                        }
                        if (label != null) {
                            label = label.toLowerCase();
                        }
                        
                        if (addressStr.contains(filterPattern) || 
                            (label != null && label.contains(filterPattern))) {
                            filtered.add(address);
                        }
                    }
                }
                
                results.values = filtered;
                results.count = filtered.size();
                return results;
            }
            
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredAddresses = (List<Address>) results.values;
                notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);
        
        application = (WalletApplication) getApplication();
        wallet = application.getWallet();
        
        database = AddressBookDatabase.getDatabase(this);
        authRequestDao = database.authenticationRequestDao();
        
        initializeViews();
        setupClickListeners();
        setupAddressSpinner();
        loadStoredRequests();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.authentication_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.authentication_options_stored_requests) {
            toggleStoredRequests();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        progressBar = findViewById(R.id.progress_bar);
        addressSpinner = findViewById(R.id.address_spinner);
        btnGenerateNewAddress = findViewById(R.id.btn_generate_new_address);
        editFilterAddress = findViewById(R.id.edit_filter_address);
        btnScanMessage = findViewById(R.id.btn_scan_message);
        textSelectedAddress = findViewById(R.id.text_selected_address);
        textSignature = findViewById(R.id.text_signature);
        qrCodeSignature = findViewById(R.id.qr_code_signature);
        btnCopySignature = findViewById(R.id.btn_copy_signature);
        btnShareSignature = findViewById(R.id.btn_share_signature);
        layoutSignatureResult = findViewById(R.id.layout_signature_result);
        layoutStoredRequests = findViewById(R.id.layout_stored_requests);
        listStoredRequests = findViewById(R.id.list_stored_requests);
    }

    private void setupClickListeners() {
        btnGenerateNewAddress.setOnClickListener(v -> generateNewAddress());
        btnScanMessage.setOnClickListener(v -> scanMessage());
        btnCopySignature.setOnClickListener(v -> copySignature());
        btnShareSignature.setOnClickListener(v -> shareSignature());
        
        // Setup filter
        editFilterAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (addressAdapter != null) {
                    addressAdapter.getFilter().filter(s);
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupAddressSpinner() {
        walletAddresses = new ArrayList<>();
        
        if (wallet != null) {
            // Get all addresses from wallet
            for (Address address : wallet.getIssuedReceiveAddresses()) {
                walletAddresses.add(address);
            }
        }
        
        addressAdapter = new AddressAdapter(this, walletAddresses);
        addressSpinner.setAdapter(addressAdapter);
        
        if (!walletAddresses.isEmpty()) {
            selectedAddress = walletAddresses.get(0);
            updateSelectedAddressDisplay();
        }
        
        addressSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedAddress = addressAdapter.getItem(position);
                updateSelectedAddressDisplay();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void updateSelectedAddressDisplay() {
        if (selectedAddress != null) {
            String label = database.addressBookDao().resolveLabel(selectedAddress.toString());
            if (label != null && !label.trim().isEmpty()) {
                textSelectedAddress.setText(label + " - " + selectedAddress.toString());
            } else {
                textSelectedAddress.setText(selectedAddress.toString());
            }
        }
    }

    private void generateNewAddress() {
        if (wallet == null) {
            Toast.makeText(this, R.string.authentication_error_no_address_selected, Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            Address newAddress = wallet.freshReceiveAddress();
            walletAddresses.add(newAddress);
            addressAdapter = new AddressAdapter(this, walletAddresses);
            addressSpinner.setAdapter(addressAdapter);
            addressSpinner.setSelection(walletAddresses.size() - 1);
            selectedAddress = newAddress;
            updateSelectedAddressDisplay();
            Toast.makeText(this, R.string.authentication_address_generated, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            log.error("Error generating new address", e);
            Toast.makeText(this, getString(R.string.authentication_error_generating_address, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }


    private void scanMessage() {
        Intent intent = new Intent(this, ScanActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SCAN_MESSAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCAN_MESSAGE && resultCode == RESULT_OK && data != null) {
            String scannedText = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            if (!TextUtils.isEmpty(scannedText)) {
                // Auto-sign the scanned message
                signMessage(scannedText);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void signMessage(String message) {
        if (selectedAddress == null) {
            Toast.makeText(this, R.string.authentication_error_no_address_selected, Toast.LENGTH_LONG).show();
            return;
        }
        
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(this, R.string.authentication_error_no_message_entered, Toast.LENGTH_LONG).show();
            return;
        }
        
        if (wallet == null) {
            Toast.makeText(this, R.string.authentication_error_no_address_selected, Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            progressBar.setVisibility(View.VISIBLE);
            
            // Get the ECKey for the selected address
            ECKey key = wallet.findKeyFromAddress(selectedAddress);
            if (key == null) {
                // Try to get a key from the wallet
                key = wallet.freshReceiveKey();
            }
            
            if (key == null) {
                Toast.makeText(this, R.string.authentication_error_no_address_selected, Toast.LENGTH_LONG).show();
                progressBar.setVisibility(View.GONE);
                return;
            }
            
            // Sign the message using Dogecoin standard format
            String signature = signMessage(key, message);
            currentSignature = signature;
            
            // Display the signature
            showSignatureResult(signature);
            
            progressBar.setVisibility(View.GONE);
        } catch (Exception e) {
            log.error("Error signing message", e);
            Toast.makeText(this, getString(R.string.authentication_error_signing_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            progressBar.setVisibility(View.GONE);
        }
    }

    private String signMessage(ECKey key, String message) {
        // Dogecoin signed message format (matches DigitalSignatureActivity)
        String messagePrefix = "Dogecoin Signed Message:\n";
        byte[] messageBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        // Create the message hash using the standard Bitcoin message signing format
        Sha256Hash messageHash = createStandardMessageHash(messagePrefix, messageBytes);
        
        // Sign the hash
        ECKey.ECDSASignature signature = key.sign(messageHash);
        
        // Create raw signature with recovery ID
        byte[] rawSignature = createRawSignature(signature, messageHash, key);
        
        // Encode to Base64
        return android.util.Base64.encodeToString(rawSignature, android.util.Base64.NO_WRAP);
    }
    
    private Sha256Hash createStandardMessageHash(String prefix, byte[] messageBytes) {
        // Standard Bitcoin message signing format: \x18Bitcoin Signed Message:\n + message
        // For Dogecoin: "Dogecoin Signed Message:\n" + message
        byte[] prefixBytes = prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        // Create message: prefix length (varint) + prefix + message length (varint) + message
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            // Write prefix length as varint
            writeVarInt(baos, prefixBytes.length);
            baos.write(prefixBytes);
            // Write message length as varint
            writeVarInt(baos, messageBytes.length);
            baos.write(messageBytes);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        
        byte[] messageToHash = baos.toByteArray();
        return Sha256Hash.twiceOf(messageToHash);
    }
    
    private void writeVarInt(java.io.ByteArrayOutputStream out, long value) throws java.io.IOException {
        if (value < 0xfd) {
            out.write((int) value);
        } else if (value <= 0xffff) {
            out.write(0xfd);
            out.write((int) (value & 0xff));
            out.write((int) ((value >> 8) & 0xff));
        } else if (value <= 0xffffffffL) {
            out.write(0xfe);
            out.write((int) (value & 0xff));
            out.write((int) ((value >> 8) & 0xff));
            out.write((int) ((value >> 16) & 0xff));
            out.write((int) ((value >> 24) & 0xff));
        } else {
            out.write(0xff);
            for (int i = 0; i < 8; i++) {
                out.write((int) ((value >> (i * 8)) & 0xff));
            }
        }
    }

    private byte[] createRawSignature(ECKey.ECDSASignature signature, Sha256Hash messageHash, ECKey key) {
        // Find recovery ID
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            try {
                ECKey recoveredKey = ECKey.recoverFromSignature(i, signature, messageHash, key.isCompressed());
                if (recoveredKey != null) {
                    // Compare full public key
                    if (java.util.Arrays.equals(recoveredKey.getPubKey(), key.getPubKey())) {
                        recId = i;
                        break;
                    }
                }
            } catch (Exception e) {
                // Continue trying
            }
        }
        
        if (recId == -1) {
            throw new RuntimeException("Could not find recovery ID");
        }
        
        // Build compact signature: 65 bytes [header(1) | R(32) | S(32)]
        int headerByte = 27 + recId + (key.isCompressed() ? 4 : 0);
        byte[] r = Utils.bigIntegerToBytes(signature.r, 32);
        byte[] s = Utils.bigIntegerToBytes(signature.s, 32);
        
        byte[] sigBytes = new byte[65];
        sigBytes[0] = (byte) headerByte;
        System.arraycopy(r, 0, sigBytes, 1, 32);
        System.arraycopy(s, 0, sigBytes, 33, 32);
        
        return sigBytes;
    }

    private void showSignatureResult(String signature) {
        textSignature.setText(signature);
        
        // Generate QR code
        Bitmap qrBitmap = Qr.bitmap(signature);
        if (qrBitmap != null) {
            qrCodeSignature.setImageBitmap(qrBitmap);
        } else {
            Toast.makeText(this, R.string.authentication_error_qr_code, Toast.LENGTH_SHORT).show();
        }
        
        layoutSignatureResult.setVisibility(View.VISIBLE);
    }

    private void copySignature() {
        if (currentSignature == null) {
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Signature", currentSignature);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.authentication_copy, Toast.LENGTH_SHORT).show();
    }

    private void shareSignature() {
        if (currentSignature == null) {
            return;
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, currentSignature);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.authentication_share)));
    }


    private void loadStoredRequests() {
        try {
            List<AuthenticationRequest> requests = authRequestDao.getAllAuthenticationRequests();
            storedRequestsAdapter = new ArrayAdapter<AuthenticationRequest>(this, android.R.layout.simple_list_item_2, android.R.id.text1, requests) {
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    AuthenticationRequest request = requests.get(position);
                    
                    TextView text1 = view.findViewById(android.R.id.text1);
                    TextView text2 = view.findViewById(android.R.id.text2);
                    
                    text1.setText(request.getLabel() != null && !request.getLabel().isEmpty() 
                        ? request.getLabel() 
                        : request.getMessage());
                    text2.setText(request.getMessage());
                    
                    return view;
                }
            };
            listStoredRequests.setAdapter(storedRequestsAdapter);
            
            listStoredRequests.setOnItemClickListener((parent, view, position, id) -> {
                AuthenticationRequest request = requests.get(position);
                signStoredRequest(request);
            });
            
            listStoredRequests.setOnItemLongClickListener((parent, view, position, id) -> {
                AuthenticationRequest request = requests.get(position);
                deleteRequest(request);
                return true;
            });
            
            if (!requests.isEmpty()) {
                layoutStoredRequests.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            log.error("Error loading stored requests", e);
        }
    }

    private void signStoredRequest(AuthenticationRequest request) {
        // Set the address
        for (int i = 0; i < walletAddresses.size(); i++) {
            if (walletAddresses.get(i).toString().equals(request.getAddress())) {
                addressSpinner.setSelection(i);
                break;
            }
        }
        
        // Sign it directly
        signMessage(request.getMessage());
    }

    private void deleteRequest(AuthenticationRequest request) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.authentication_delete_confirm_title)
            .setMessage(R.string.authentication_delete_confirm_message)
            .setPositiveButton(R.string.button_ok, (dialog, which) -> {
                try {
                    authRequestDao.deleteAuthenticationRequest(request);
                    Toast.makeText(this, R.string.authentication_request_deleted, Toast.LENGTH_SHORT).show();
                    loadStoredRequests();
                } catch (Exception e) {
                    log.error("Error deleting request", e);
                    Toast.makeText(this, "Error deleting request: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }

    private void toggleStoredRequests() {
        if (layoutStoredRequests.getVisibility() == View.VISIBLE) {
            layoutStoredRequests.setVisibility(View.GONE);
        } else {
            layoutStoredRequests.setVisibility(View.VISIBLE);
            loadStoredRequests();
        }
    }
}

