package de.schildbach.wallet.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.cardview.widget.CardView;
import com.google.common.base.Strings;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.PaymentTerminalApiService;
import de.schildbach.wallet.data.SelectedExchangeRateLiveData;
import de.schildbach.wallet.ui.CurrencyAmountView;
import de.schildbach.wallet.ui.CurrencyCalculatorLink;
import de.schildbach.wallet.util.Qr;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Payment Terminal Mode - Secure payment terminal for POS systems
 * 
 * This activity provides a locked-down interface for receiving payments:
 * - PIN-protected exit
 * - Displays QR code for payment requests
 * - Monitors for incoming payments
 * - Integration with POS systems via HTTP API
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class PaymentTerminalActivity extends AbstractWalletActivity
        implements PaymentTerminalApiService.PaymentRequestCallback {
    private WalletApplication application;
    private Configuration config;
    private Address receiveAddress;
    private Coin requestedAmount;
    private Transaction receivedTransaction;
    private volatile boolean isWaitingForPayment = false;
    private PaymentTerminalApiService apiService;
    private Wallet wallet;
    private boolean isVerifiedToExit = false;
    
    private ImageView qrView;
    private CardView qrCardView;
    private CurrencyAmountView amountBtcView;
    private CurrencyAmountView amountLocalView;
    private CurrencyCalculatorLink amountCalculatorLink;
    private TextView waitingTextView;
    private TextView addressTextView;
    private TextView amountDisplayTextView;
    private TextView inputSectionTitle;
    private View amountInputContainer;
    private Button generateButton;
    private Button cancelButton;
    private Button newRequestButton;
    private TextView exitButton;
    
    private static final Logger log = LoggerFactory.getLogger(PaymentTerminalActivity.class);
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        application = getWalletApplication();
        config = application.getConfiguration();
        
        // Check if terminal mode is enabled
        if (!config.getPaymentTerminalEnabled()) {
            android.widget.Toast.makeText(this, "Terminal mode is not enabled", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        setContentView(R.layout.activity_payment_terminal);
        
        initViews();
        
        // Setup exchange rate observer for currency calculator
        if (Constants.ENABLE_EXCHANGE_RATES) {
            final SelectedExchangeRateLiveData exchangeRate = new SelectedExchangeRateLiveData(application);
            exchangeRate.observe(this, rate -> {
                if (rate != null && amountCalculatorLink != null) {
                    amountCalculatorLink.setExchangeRate(rate.exchangeRate());
                }
            });
        }
        
        // Setup OnBackPressedDispatcher to handle back button
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Back button pressed - request PIN to exit
                requestPinToExit();
            }
        });
        
        // Start blockchain service to monitor for payments
        BlockchainService.ensureRunning(this);
        
        // Start API server immediately - it will handle context issues gracefully
        apiService = new PaymentTerminalApiService(application);
        apiService.setPaymentRequestCallback(this);
        apiService.start();
        log.info("Payment Terminal API server started on localhost:{}", apiService.getPort());
        
        // Load wallet asynchronously
        application.getWalletAsync(wallet -> {
            this.wallet = wallet;
            if (wallet != null) {
                wallet.addCoinsReceivedEventListener(
                    org.bitcoinj.utils.Threading.USER_THREAD, 
                    walletCoinsReceivedEventListener
                );
            }
            // Get fresh receive address after wallet is loaded
            runOnUiThread(this::updateReceiveAddress);
        });
    }
    
    private void initViews() {
        qrView = findViewById(R.id.terminal_qr_view);
        qrCardView = findViewById(R.id.terminal_qr_card);
        amountBtcView = findViewById(R.id.terminal_amount_btc);
        amountLocalView = findViewById(R.id.terminal_amount_local);
        waitingTextView = findViewById(R.id.terminal_waiting);
        addressTextView = findViewById(R.id.terminal_address);
        amountDisplayTextView = findViewById(R.id.terminal_amount_display);
        inputSectionTitle = findViewById(R.id.terminal_input_section_title);
        amountInputContainer = findViewById(R.id.terminal_amount_input_container);
        generateButton = findViewById(R.id.terminal_generate_button);
        cancelButton = findViewById(R.id.terminal_cancel_button);
        newRequestButton = findViewById(R.id.terminal_new_request_button);
        exitButton = findViewById(R.id.terminal_exit_button);
        
        qrCardView.setCardBackgroundColor(Color.WHITE);
        qrCardView.setPreventCornerOverlap(false);
        qrCardView.setUseCompatPadding(false);
        qrCardView.setMaxCardElevation(0);
        
        // Setup currency calculator link
        amountCalculatorLink = new CurrencyCalculatorLink(amountBtcView, amountLocalView);
        amountBtcView.setCurrencySymbol(config.getFormat().code());
        amountBtcView.setInputFormat(config.getMaxPrecisionFormat());
        amountBtcView.setHintFormat(config.getFormat());
        amountLocalView.setInputFormat(Constants.LOCAL_FORMAT);
        amountLocalView.setHintFormat(Constants.LOCAL_FORMAT);
        
        generateButton.setOnClickListener(v -> generatePaymentRequest());
        cancelButton.setOnClickListener(v -> resetForNewRequest());
        newRequestButton.setOnClickListener(v -> resetForNewRequest());
        exitButton.setOnClickListener(v -> {
            log.info("Exit button clicked");
            requestPinToExit();
        });
        
        // Make the button unclickable if we're in wait-for-PIN state
        exitButton.setClickable(true);
        exitButton.setEnabled(true);
        
        // Initially hide waiting state and cancel button
        qrCardView.setVisibility(View.GONE);
        waitingTextView.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        newRequestButton.setVisibility(View.GONE);
        addressTextView.setVisibility(View.GONE);
        amountDisplayTextView.setVisibility(View.GONE);
    }
    
    private void generatePaymentRequest() {
        final Coin amount = amountCalculatorLink.getAmount();
        
        if (amount == null || amount.isZero() || amount.isNegative()) {
            android.widget.Toast.makeText(this, R.string.payment_terminal_enter_amount, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        requestedAmount = amount;
        
        if (receiveAddress == null) {
            android.widget.Toast.makeText(this, "Generating address...", android.widget.Toast.LENGTH_SHORT).show();
            updateReceiveAddress();
            // Give it a moment to generate
            new android.os.Handler().postDelayed(this::generatePaymentRequest, 500);
            return;
        }
        
        // Generate QR code
        generateQrCode();
        
        // Show QR code and related info
        qrCardView.setVisibility(View.VISIBLE);
        addressTextView.setText(receiveAddress.toString());
        amountDisplayTextView.setText(config.getFormat().format(requestedAmount));
        addressTextView.setVisibility(View.VISIBLE);
        amountDisplayTextView.setVisibility(View.VISIBLE);
        
        // Hide input fields and section, show cancel button
        inputSectionTitle.setVisibility(View.GONE);
        amountInputContainer.setVisibility(View.GONE);
        waitingTextView.setVisibility(View.VISIBLE);
        generateButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        isWaitingForPayment = true;
        
        // Start monitoring for payment
        startPaymentMonitoring();
    }
    
    private void generateQrCode() {
        if (receiveAddress == null || requestedAmount == null) {
            return;
        }
        
        // Create Bitcoin URI
        final String uri = createBitcoinUri(receiveAddress, requestedAmount);
        
        // Generate QR code
        final Bitmap qrBitmap = Qr.bitmap(uri);
        if (qrBitmap != null) {
            final BitmapDrawable qrDrawable = new BitmapDrawable(getResources(), qrBitmap);
            qrDrawable.setFilterBitmap(false);
            qrView.setImageDrawable(qrDrawable);
        }
    }
    
    private String createBitcoinUri(final Address address, final Coin amount) {
        final StringBuilder uri = new StringBuilder("dogecoin:");
        uri.append(address.toString());
        if (amount != null && !amount.equals(Coin.ZERO)) {
            // Convert coins to decimal string
            String amountStr = String.valueOf((double) amount.value / Coin.COIN.value);
            uri.append("?amount=").append(amountStr);
        }
        return uri.toString();
    }
    
    private void updateReceiveAddress() {
        final Wallet wallet = this.wallet;
        if (wallet != null) {
            try {
                receiveAddress = wallet.freshReceiveAddress();
                log.info("Terminal receive address: {}", receiveAddress);
            } catch (final IllegalStateException x) {
                log.error("problem determining receive address", x);
            }
        }
    }
    
    private void startPaymentMonitoring() {
        // Monitor wallet for received coins matching the requested amount
        // This is handled by the wallet listener
    }
    
    @Override
    public void onPaymentRequestReceived(Address address, Coin amount, String uri) {
        log.info("Payment request received from API: address={}, amount={}", address, amount);
        
        // Update UI on main thread
        runOnUiThread(() -> {
            receiveAddress = address;
            requestedAmount = amount;
            
            // Generate QR code
            generateQrCode();
            
            // Show QR code and related info
            qrCardView.setVisibility(View.VISIBLE);
            addressTextView.setText(receiveAddress.toString());
            amountDisplayTextView.setText(config.getFormat().format(requestedAmount));
            addressTextView.setVisibility(View.VISIBLE);
            amountDisplayTextView.setVisibility(View.VISIBLE);
            
            // Hide input fields and section, show cancel button
            inputSectionTitle.setVisibility(View.GONE);
            amountInputContainer.setVisibility(View.GONE);
            waitingTextView.setVisibility(View.VISIBLE);
            generateButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.VISIBLE);
            isWaitingForPayment = true;
            
            // Start monitoring for payment
            startPaymentMonitoring();
            
            // Show toast notification
            android.widget.Toast.makeText(this, "Payment request received: " + config.getFormat().format(amount), 
                android.widget.Toast.LENGTH_LONG).show();
        });
    }
    
    private void resetForNewRequest() {
        // Reset state for a new payment request
        amountBtcView.setAmount(null, false);
        amountLocalView.setAmount(null, false);
        qrView.setImageDrawable(null);
        qrCardView.setVisibility(View.GONE);
        waitingTextView.setVisibility(View.GONE);
        addressTextView.setVisibility(View.GONE);
        amountDisplayTextView.setVisibility(View.GONE);
        
        // Show input fields again
        inputSectionTitle.setVisibility(View.VISIBLE);
        amountInputContainer.setVisibility(View.VISIBLE);
        generateButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.GONE);
        newRequestButton.setVisibility(View.GONE);
        isWaitingForPayment = false;
        receivedTransaction = null;
        requestedAmount = null;
        amountBtcView.requestFocus();
    }
    
    private final WalletCoinsReceivedEventListener walletCoinsReceivedEventListener =
            new WalletCoinsReceivedEventListener() {
        @Override
        public void onCoinsReceived(final Wallet wallet, final Transaction tx, 
                                   final Coin prevBalance, final Coin newBalance) {
            if (!isWaitingForPayment) {
                return;
            }
            
            // Check if this payment matches our requested amount
            final Coin receivedAmount = tx.getValue(wallet);
            log.info("Terminal received transaction: {}, amount: {}", tx.getTxId(), receivedAmount);
            
            if (receivedAmount.compareTo(requestedAmount) >= 0) {
                // Payment received!
                receivedTransaction = tx;
                
                runOnUiThread(() -> {
                    waitingTextView.setText(getString(R.string.payment_terminal_payment_received));
                    waitingTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark, getTheme()));
                    cancelButton.setVisibility(View.GONE);
                    newRequestButton.setVisibility(View.VISIBLE);
                    isWaitingForPayment = false;
                    
                    android.widget.Toast.makeText(PaymentTerminalActivity.this, 
                        getString(R.string.payment_terminal_payment_received), 
                        android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }
    };
    
    
    private void requestPinToExit() {
        final android.widget.EditText pinEditText = new android.widget.EditText(this);
        pinEditText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | 
                                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinEditText.setHint("Enter PIN");
        
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.payment_terminal_enter_pin_title)
                .setMessage("Enter PIN to exit terminal mode")
                .setView(pinEditText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String enteredPin = pinEditText.getText().toString();
                    String savedPin = config.getPaymentTerminalPin();
                    if (savedPin != null && savedPin.equals(enteredPin)) {
                        // PIN is correct - disable terminal mode and exit
                        config.setPaymentTerminalEnabled(false);
                        // Clear the PIN when disabling terminal mode
                        config.setPaymentTerminalPin(null);
                        isVerifiedToExit = true;
                        
                        // Start the normal wallet activity before finishing terminal activity
                        final Intent intent = new Intent(this, WalletActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        
                        finish();
                    } else {
                        android.widget.Toast.makeText(this, R.string.payment_terminal_wrong_pin, android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(true)
                .show();
    }
    
    @Override
    protected void onDestroy() {
        if (wallet != null) {
            wallet.removeCoinsReceivedEventListener(walletCoinsReceivedEventListener);
        }
        
        // Stop API server
        if (apiService != null) {
            apiService.stop();
        }
        
        super.onDestroy();
    }
    
    @Override
    public void finish() {
        log.info("finish() called - isVerifiedToExit: {}", isVerifiedToExit);
        if (!isVerifiedToExit) {
            log.info("Blocking finish() - must verify PIN first");
            requestPinToExit();
            return;
        }
        super.finish();
    }
}

