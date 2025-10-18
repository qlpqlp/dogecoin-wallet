package de.schildbach.wallet.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import de.schildbach.wallet.ui.AbstractWalletActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.util.WalletUtils;
import de.schildbach.wallet.addressbook.AddressBookDao;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.WalletLiveData;
import de.schildbach.wallet.ui.WalletTransactionsViewModel;
import de.schildbach.wallet.ui.send.SendCoinsActivity;
import de.schildbach.wallet.util.WalletUtils;

/**
 * Activity for generating accounting reports with transaction visualization and export capabilities
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class AccountingReportsActivity extends AbstractWalletActivity {
    private static final Logger log = LoggerFactory.getLogger(AccountingReportsActivity.class);
    
    private static final int REQUEST_CODE_SEND_COINS = 1;
    
    private WalletApplication application;
    private AddressBookDao addressBookDao;
    
    private Button btnStartDate;
    private Button btnEndDate;
    private Button btnLoadData;
    private com.github.mikephil.charting.charts.LineChart timelineChart;
    private Button btnExportCsv;
    private Button btnExportJson;
    private Button btnExportPdf;
    private TextView txtExportStatus;
    
    private Date startDate;
    private Date endDate;
    private List<TransactionData> transactionDataList;
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        log.debug("=== ACCOUNTING REPORTS ONCREATE START ===");
        
        super.onCreate(savedInstanceState);
        log.debug("Super onCreate completed");
        
        setContentView(R.layout.activity_accounting_reports);
        log.debug("Content view set");
        
        application = getWalletApplication();
        log.debug("Wallet application: {}", application != null ? "not null" : "null");
        
        addressBookDao = AddressBookDatabase.getDatabase(this).addressBookDao();
        log.debug("Address book DAO: {}", addressBookDao != null ? "not null" : "null");
        
        log.debug("Initializing views...");
        initializeViews();
        log.debug("Views initialized");
        
        log.debug("Setting up date pickers...");
        setupDatePickers();
        log.debug("Date pickers set up");
        
        log.debug("Setting up chart...");
        setupChart();
        log.debug("Chart set up");
        
        // Set default date range to last 30 days
        Calendar calendar = Calendar.getInstance();
        endDate = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -30);
        startDate = calendar.getTime();
        log.debug("Default date range set: {} to {}", startDate, endDate);
        
        log.debug("Updating date buttons...");
        updateDateButtons();
        log.debug("Date buttons updated");
        
        // Automatically load data for the last 30 days with a small delay
        log.debug("Auto-loading data for last 30 days...");
        new android.os.Handler().postDelayed(() -> {
            loadTransactionData();
        }, 500); // 500ms delay to ensure activity is fully initialized
        
        log.debug("=== ACCOUNTING REPORTS ONCREATE END ===");
    }
    
    private void initializeViews() {
        btnStartDate = findViewById(R.id.btn_start_date);
        btnEndDate = findViewById(R.id.btn_end_date);
        btnLoadData = findViewById(R.id.btn_load_data);
        timelineChart = findViewById(R.id.timeline_chart);
        btnExportCsv = findViewById(R.id.btn_export_csv);
        btnExportJson = findViewById(R.id.btn_export_json);
        btnExportPdf = findViewById(R.id.btn_export_pdf);
        txtExportStatus = findViewById(R.id.txt_export_status);
        
        btnLoadData.setOnClickListener(v -> loadTransactionData());
        btnExportCsv.setOnClickListener(v -> exportTransactions(ExportFormat.CSV));
        btnExportJson.setOnClickListener(v -> exportTransactions(ExportFormat.JSON));
        btnExportPdf.setOnClickListener(v -> exportTransactions(ExportFormat.PDF));
    }
    
    private void setupDatePickers() {
        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));
    }
    
    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();
        if (isStartDate && startDate != null) {
            calendar.setTime(startDate);
        } else if (!isStartDate && endDate != null) {
            calendar.setTime(endDate);
        }
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(year, month, dayOfMonth);
                    Date selectedDate = selectedCalendar.getTime();
                    
                    if (isStartDate) {
                        startDate = selectedDate;
                    } else {
                        endDate = selectedDate;
                    }
                    
                    updateDateButtons();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        
        datePickerDialog.show();
    }
    
    private void updateDateButtons() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        if (startDate != null) {
            btnStartDate.setText(dateFormat.format(startDate));
        }
        
        if (endDate != null) {
            btnEndDate.setText(dateFormat.format(endDate));
        }
    }
    
    private void setupChart() {
        try {
            log.debug("Setting up modern chart...");
            
            // Configure chart appearance
            timelineChart.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            timelineChart.setDrawGridBackground(false);
            timelineChart.setDrawBorders(false);
            timelineChart.setTouchEnabled(true);
            timelineChart.setDragEnabled(true);
            timelineChart.setScaleEnabled(true);
            timelineChart.setPinchZoom(true);
            timelineChart.setDoubleTapToZoomEnabled(true);
            
            // Configure description
            com.github.mikephil.charting.components.Description description = timelineChart.getDescription();
            description.setEnabled(false);
            
            // Configure legend
            com.github.mikephil.charting.components.Legend legend = timelineChart.getLegend();
            legend.setEnabled(true);
            legend.setVerticalAlignment(com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
            legend.setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
            legend.setOrientation(com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
            legend.setDrawInside(false);
            legend.setTextSize(12f);
            legend.setTextColor(ContextCompat.getColor(this, R.color.fg_less_significant));
            
            // Configure X axis
            com.github.mikephil.charting.components.XAxis xAxis = timelineChart.getXAxis();
            xAxis.setEnabled(true);
            xAxis.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setDrawAxisLine(true);
            xAxis.setDrawLabels(true);
            xAxis.setTextSize(10f);
            xAxis.setTextColor(ContextCompat.getColor(this, R.color.fg_less_significant));
            xAxis.setGranularity(1f);
            xAxis.setLabelRotationAngle(-45f);
            
            // Configure left Y axis
            com.github.mikephil.charting.components.YAxis leftAxis = timelineChart.getAxisLeft();
            leftAxis.setEnabled(true);
            leftAxis.setDrawGridLines(true);
            leftAxis.setDrawAxisLine(true);
            leftAxis.setDrawLabels(true);
            leftAxis.setTextSize(10f);
            leftAxis.setTextColor(ContextCompat.getColor(this, R.color.fg_less_significant));
            leftAxis.setGridColor(ContextCompat.getColor(this, R.color.fg_less_significant));
            leftAxis.setAxisLineColor(ContextCompat.getColor(this, R.color.fg_less_significant));
            leftAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format("%.2f", value) + " DOGE";
                }
            });
            
            // Configure right Y axis
            com.github.mikephil.charting.components.YAxis rightAxis = timelineChart.getAxisRight();
            rightAxis.setEnabled(false);
            
            // Set empty data initially
            timelineChart.setData(null);
            timelineChart.setVisibility(View.VISIBLE);
            
            log.debug("Modern chart setup completed");
        } catch (Exception e) {
            log.error("Error setting up chart", e);
        }
    }
    
    private void loadTransactionData() {
        log.debug("=== LOAD TRANSACTION DATA START ===");
        
        if (startDate == null || endDate == null) {
            log.debug("Start or end date is null - startDate: {}, endDate: {}", startDate, endDate);
            Toast.makeText(this, "Please select both start and end dates", Toast.LENGTH_SHORT).show();
            return;
        }
        
        log.debug("Date range: {} to {}", startDate, endDate);
        
        if (startDate.after(endDate)) {
            log.debug("Start date is after end date");
            Toast.makeText(this, "Start date must be before end date", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Load transactions from wallet
        if (application == null) {
            log.error("Wallet application is null!");
            Toast.makeText(this, "Wallet application not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        log.debug("Creating WalletLiveData...");
        try {
            WalletLiveData walletLiveData = new WalletLiveData(application);
            log.debug("WalletLiveData created successfully");
            
            log.debug("Setting up observer...");
            walletLiveData.observe(this, wallet -> {
                log.debug("Wallet observer triggered - wallet: {}", wallet != null ? "not null" : "null");
                if (wallet != null) {
                    try {
                        log.debug("Getting transactions from wallet...");
                        List<Transaction> transactions = new ArrayList<>(wallet.getTransactions(false));
                        log.debug("Found {} transactions", transactions.size());
                        
                        // Only process if we have transactions
                        if (!transactions.isEmpty()) {
                            log.debug("Processing transactions...");
                            processTransactions(transactions, wallet);
                            log.debug("Transaction processing completed");
                        } else {
                            log.debug("No transactions found, skipping processing");
                        }
                    } catch (Exception e) {
                        log.error("Error processing transactions", e);
                        Toast.makeText(this, "Error loading transactions", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    log.warn("Wallet is null in observer");
                }
            });
            log.debug("Observer setup completed");
        } catch (Exception e) {
            log.error("Error creating WalletLiveData or setting up observer", e);
            Toast.makeText(this, "Error setting up wallet observer", Toast.LENGTH_SHORT).show();
        }
        
        log.debug("=== LOAD TRANSACTION DATA END ===");
    }
    
    private void processTransactions(List<Transaction> transactions, Wallet wallet) {
        log.debug("=== PROCESS TRANSACTIONS START ===");
        log.debug("Processing {} transactions", transactions.size());
        
        transactionDataList = new ArrayList<>();
        
        try {
            int processedCount = 0;
            int filteredCount = 0;
            
            for (Transaction tx : transactions) {
                try {
                    log.debug("Processing transaction: {}", tx.getTxId());
                    
                    Date txDate = new Date(tx.getUpdateTime().getTime());
                    log.debug("Transaction date: {}", txDate);
                    
                    // Check if transaction is within date range
                    if (txDate.before(startDate) || txDate.after(endDate)) {
                        log.debug("Transaction filtered out due to date range");
                        filteredCount++;
                        continue;
                    }
                    
                    log.debug("Transaction within date range, processing...");
                    
                    TransactionData txData = new TransactionData();
                    txData.txid = tx.getTxId().toString();
                    txData.timestamp = txDate;
                    
                    log.debug("Getting transaction value...");
                    // Use the same logic as the main wallet transaction list
                    Coin value = tx.getValue(wallet);
                    log.debug("Transaction value: {}", value);
                    
                    // Determine transaction type using the same logic as main wallet
                    boolean sent = value.signum() < 0;
                    boolean self = WalletUtils.isEntirelySelf(tx, wallet);
                    log.debug("Sent: {}, Self: {}", sent, self);
                    
                    if (self) {
                        // Internal transaction - amount is zero, but we still pay fees
                        txData.type = "internal";
                        txData.amount = Coin.ZERO; // Internal transactions have zero amount
                        log.debug("Internal transaction amount: {}", txData.amount);
                    } else if (sent) {
                        txData.type = "send";
                        // For send transactions, show net amount (amount - fee) that was actually transferred
                        Coin fee = tx.getFee();
                        txData.amount = value.negate().subtract(fee); // Net amount sent (positive)
                        log.debug("Send transaction amount (net): {}", txData.amount);
                    } else {
                        txData.type = "receive";
                        txData.amount = value; // Received amount
                        log.debug("Receive transaction amount: {}", txData.amount);
                    }
                    
                    log.debug("Transaction type: {}, amount: {}", txData.type, txData.amount);
                    
                    // Get fee - only for sent and internal transactions
                    if (sent || self) {
                        txData.fee = tx.getFee();
                    } else {
                        txData.fee = Coin.ZERO; // No fee for received transactions
                    }
                    log.debug("Transaction fee: {}", txData.fee);
                    
                    // Process inputs and outputs for addresses
                    log.debug("Processing transaction addresses...");
                    processTransactionAddresses(tx, txData, wallet);
                    
                    // Extract notes from OP_RETURN
                    log.debug("Extracting notes from transaction...");
                    txData.notes = extractNotesFromTransaction(tx);
                    
                    transactionDataList.add(txData);
                    processedCount++;
                    log.debug("Transaction processed successfully");
                } catch (Exception e) {
                    log.error("Error processing transaction: " + tx.getTxId(), e);
                    // Continue processing other transactions even if one fails
                }
            }
            
            log.debug("Transaction processing summary - Processed: {}, Filtered: {}, Total: {}", 
                     processedCount, filteredCount, transactions.size());
        } catch (Exception e) {
            log.error("Error processing transactions", e);
            Toast.makeText(this, "Error processing transactions: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        
        log.debug("Updating timeline chart...");
        updateTimelineChart();
        log.debug("Updating export buttons...");
        updateExportButtons();
        
        log.debug("=== PROCESS TRANSACTIONS END ===");
    }
    
    private void processTransactionAddresses(Transaction tx, TransactionData txData, Wallet wallet) {
        try {
            // Use the same address logic as the main wallet transaction list
            Coin value = tx.getValue(wallet);
            boolean sent = value.signum() < 0;
            boolean self = WalletUtils.isEntirelySelf(tx, wallet);
            
            if (self) {
                // Internal transaction - show both from and to addresses
                txData.from = "Internal";
                txData.fromLabel = "Internal";
                txData.to = "Internal";
                txData.toLabel = "Internal";
            } else {
                // Use the same address resolution as main wallet
                Address address = sent ? WalletUtils.getToAddressOfSent(tx, wallet)
                        : WalletUtils.getWalletAddressOfReceived(tx, wallet);
                
                if (address != null) {
                    String addressStr = address.toString();
                    if (sent) {
                        txData.to = addressStr;
                        // Get label from address book
                        if (addressBookDao != null) {
                            try {
                                String label = addressBookDao.resolveLabel(addressStr);
                                if (label != null) {
                                    txData.toLabel = label;
                                }
                            } catch (Exception e) {
                                log.warn("Error resolving label for address: " + addressStr, e);
                            }
                        }
                    } else {
                        txData.from = addressStr;
                        // Get label from address book
                        if (addressBookDao != null) {
                            try {
                                String label = addressBookDao.resolveLabel(addressStr);
                                if (label != null) {
                                    txData.fromLabel = label;
                                }
                            } catch (Exception e) {
                                log.warn("Error resolving label for address: " + addressStr, e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing transaction addresses", e);
            // Continue processing other transactions even if one fails
        }
    }
    
    private String extractNotesFromTransaction(Transaction tx) {
        // Look for OP_RETURN outputs
        for (TransactionOutput output : tx.getOutputs()) {
            if (output.getScriptPubKey().isOpReturn()) {
                try {
                    byte[] data = output.getScriptPubKey().getProgram();
                    if (data.length > 1) {
                        // Skip the OP_RETURN opcode (0x6a)
                        byte[] message = new byte[data.length - 1];
                        System.arraycopy(data, 1, message, 0, message.length);
                        return new String(message, "UTF-8");
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract OP_RETURN data", e);
                }
            }
        }
        return "";
    }
    
    
    private void updateTimelineChart() {
        log.debug("=== UPDATE TIMELINE CHART START ===");
        
        try {
            if (transactionDataList == null || transactionDataList.isEmpty()) {
                log.debug("No transaction data available");
                timelineChart.clear();
                timelineChart.setNoDataText("No transactions found for the selected date range");
                timelineChart.setNoDataTextColor(ContextCompat.getColor(this, R.color.fg_less_significant));
                return;
            }
            
            log.debug("Processing {} transaction data entries for modern chart", transactionDataList.size());
            
            // Group transactions by date and calculate daily totals (sorted by date)
            Map<String, DailyData> dailyDataMap = new LinkedHashMap<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            
            // Sort transactions by date (oldest first for chart timeline)
            List<TransactionData> sortedForChart = new ArrayList<>(transactionDataList);
            sortedForChart.sort((tx1, tx2) -> tx1.timestamp.compareTo(tx2.timestamp));
            
            for (TransactionData tx : sortedForChart) {
                String dateKey = dateFormat.format(tx.timestamp);
                DailyData dailyData = dailyDataMap.getOrDefault(dateKey, new DailyData());
                
                if ("send".equals(tx.type)) {
                    dailyData.sentAmount = dailyData.sentAmount.add(tx.amount);
                    dailyData.sentCount++;
                } else if ("receive".equals(tx.type)) {
                    dailyData.receivedAmount = dailyData.receivedAmount.add(tx.amount);
                    dailyData.receivedCount++;
                } else if ("internal".equals(tx.type)) {
                    // For internal transactions, we can show them as both sent and received
                    // or create a separate category. For now, let's show them as received
                    dailyData.receivedAmount = dailyData.receivedAmount.add(tx.amount);
                    dailyData.receivedCount++;
                }
                
                dailyDataMap.put(dateKey, dailyData);
            }
            
            // Create chart entries
            List<com.github.mikephil.charting.data.Entry> sentEntries = new ArrayList<>();
            List<com.github.mikephil.charting.data.Entry> receivedEntries = new ArrayList<>();
            List<String> xLabels = new ArrayList<>();
            
            int index = 0;
            for (Map.Entry<String, DailyData> entry : dailyDataMap.entrySet()) {
                String date = entry.getKey();
                DailyData data = entry.getValue();
                
                // Add entries (only if amount > 0)
                if (data.sentAmount.compareTo(Coin.ZERO) > 0) {
                    sentEntries.add(new com.github.mikephil.charting.data.Entry(index, data.sentAmount.longValue() / 100000000.0f));
                }
                if (data.receivedAmount.compareTo(Coin.ZERO) > 0) {
                    receivedEntries.add(new com.github.mikephil.charting.data.Entry(index, data.receivedAmount.longValue() / 100000000.0f));
                }
                
                xLabels.add(date);
                index++;
            }
            
            // Create datasets
            com.github.mikephil.charting.data.LineDataSet sentDataSet = new com.github.mikephil.charting.data.LineDataSet(sentEntries, "Sent");
            sentDataSet.setColor(ContextCompat.getColor(this, R.color.fg_value_negative));
            sentDataSet.setCircleColor(ContextCompat.getColor(this, R.color.fg_value_negative));
            sentDataSet.setLineWidth(3f);
            sentDataSet.setCircleRadius(4f);
            sentDataSet.setDrawValues(false);
            sentDataSet.setDrawFilled(true);
            sentDataSet.setFillColor(ContextCompat.getColor(this, R.color.fg_value_negative));
            sentDataSet.setFillAlpha(50);
            sentDataSet.setDrawCircleHole(false);
            
            com.github.mikephil.charting.data.LineDataSet receivedDataSet = new com.github.mikephil.charting.data.LineDataSet(receivedEntries, "Received");
            receivedDataSet.setColor(ContextCompat.getColor(this, R.color.fg_value_positive));
            receivedDataSet.setCircleColor(ContextCompat.getColor(this, R.color.fg_value_positive));
            receivedDataSet.setLineWidth(3f);
            receivedDataSet.setCircleRadius(4f);
            receivedDataSet.setDrawValues(false);
            receivedDataSet.setDrawFilled(true);
            receivedDataSet.setFillColor(ContextCompat.getColor(this, R.color.fg_value_positive));
            receivedDataSet.setFillAlpha(50);
            receivedDataSet.setDrawCircleHole(false);
            
            // Create line data
            com.github.mikephil.charting.data.LineData lineData = new com.github.mikephil.charting.data.LineData();
            lineData.addDataSet(sentDataSet);
            lineData.addDataSet(receivedDataSet);
            
            // Set X axis labels
            com.github.mikephil.charting.components.XAxis xAxis = timelineChart.getXAxis();
            xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int index = (int) value;
                    if (index >= 0 && index < xLabels.size()) {
                        return xLabels.get(index);
                    }
                    return "";
                }
            });
            
            // Set data and refresh
            timelineChart.setData(lineData);
            timelineChart.invalidate();
            
            log.debug("Modern chart updated successfully with {} data points", xLabels.size());
        } catch (Exception e) {
            log.error("Error updating timeline chart", e);
            Toast.makeText(this, "Error updating chart: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        log.debug("=== UPDATE TIMELINE CHART END ===");
    }
    
    // Helper class for daily transaction data
    private static class DailyData {
        Coin sentAmount = Coin.ZERO;
        Coin receivedAmount = Coin.ZERO;
        int sentCount = 0;
        int receivedCount = 0;
    }
    
    private void updateExportButtons() {
        boolean hasData = transactionDataList != null && !transactionDataList.isEmpty();
        btnExportCsv.setEnabled(hasData);
        btnExportJson.setEnabled(hasData);
        btnExportPdf.setEnabled(hasData);
        
        if (!hasData) {
            txtExportStatus.setText(getString(R.string.accounting_reports_no_data));
            txtExportStatus.setVisibility(View.VISIBLE);
        } else {
            txtExportStatus.setVisibility(View.GONE);
        }
    }
    
    private void exportTransactions(ExportFormat format) {
        if (transactionDataList == null || transactionDataList.isEmpty()) {
            Toast.makeText(this, "No data available for export. Please load data first.", Toast.LENGTH_LONG).show();
            return;
        }
        
        txtExportStatus.setText(getString(R.string.accounting_reports_exporting));
        txtExportStatus.setVisibility(View.VISIBLE);
        
        // Run export in background thread
        new Thread(() -> {
            try {
                File exportFile = createExportFile(format);
                boolean success = writeExportFile(exportFile, format);
                
                runOnUiThread(() -> {
                    if (success) {
                        txtExportStatus.setText(getString(R.string.accounting_reports_export_success));
                        log.debug("Export successful, file created: {}", exportFile.getAbsolutePath());
                        shareExportFile(exportFile);
                    } else {
                        txtExportStatus.setText(getString(R.string.accounting_reports_export_error));
                        log.error("Export failed");
                    }
                });
            } catch (Exception e) {
                log.error("Export failed", e);
                runOnUiThread(() -> {
                    txtExportStatus.setText(getString(R.string.accounting_reports_export_error));
                });
            }
        }).start();
    }
    
    private File createExportFile(ExportFormat format) throws IOException {
        String fileName = "dogecoin_transactions_" + 
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + 
            "." + format.getExtension();
        
        File exportDir = new File(getExternalFilesDir(null), "exports");
        if (!exportDir.exists()) {
            boolean created = exportDir.mkdirs();
            log.debug("Export directory created: {}", created);
        }
        
        File exportFile = new File(exportDir, fileName);
        log.debug("Creating export file: {}", exportFile.getAbsolutePath());
        return exportFile;
    }
    
    private boolean writeExportFile(File file, ExportFormat format) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            switch (format) {
                case CSV:
                    return writeCsvFile(fos);
                case JSON:
                    return writeJsonFile(fos);
                case PDF:
                    return writePdfFile(fos);
                default:
                    return false;
            }
        } catch (IOException e) {
            log.error("Failed to write export file", e);
            return false;
        }
    }
    
    private boolean writeCsvFile(FileOutputStream fos) throws IOException {
        // Sort transactions by date (oldest first for exports)
        List<TransactionData> sortedTransactions = new ArrayList<>(transactionDataList);
        sortedTransactions.sort((tx1, tx2) -> tx1.timestamp.compareTo(tx2.timestamp));
        
        StringBuilder csv = new StringBuilder();
        csv.append("txid,type,timestamp,from_label,from,to_label,to,amount,fee,notes\n");
        
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        
        for (TransactionData tx : sortedTransactions) {
            csv.append(tx.txid).append(",");
            csv.append(tx.type).append(",");
            csv.append(isoFormat.format(tx.timestamp)).append(",");
            csv.append(escapeCsv(tx.fromLabel)).append(",");
            csv.append(escapeCsv(tx.from)).append(",");
            csv.append(escapeCsv(tx.toLabel)).append(",");
            csv.append(escapeCsv(tx.to)).append(",");
            csv.append(String.format("%.8f", tx.amount.longValue() / 100000000.0)).append(",");
            csv.append(tx.fee != null ? String.format("%.8f", tx.fee.longValue() / 100000000.0) : "0.00000000").append(",");
            csv.append(escapeCsv(tx.notes)).append("\n");
        }
        
        fos.write(csv.toString().getBytes("UTF-8"));
        return true;
    }
    
    private boolean writeJsonFile(FileOutputStream fos) throws IOException {
        // Sort transactions by date (oldest first for exports)
        List<TransactionData> sortedTransactions = new ArrayList<>(transactionDataList);
        sortedTransactions.sort((tx1, tx2) -> tx1.timestamp.compareTo(tx2.timestamp));
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"transactions\": [\n");
        
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        
        for (int i = 0; i < sortedTransactions.size(); i++) {
            TransactionData tx = sortedTransactions.get(i);
            json.append("    {\n");
            json.append("      \"txid\": \"").append(escapeJson(tx.txid)).append("\",\n");
            json.append("      \"type\": \"").append(escapeJson(tx.type)).append("\",\n");
            json.append("      \"timestamp\": \"").append(isoFormat.format(tx.timestamp)).append("\",\n");
            json.append("      \"from_label\": \"").append(escapeJson(tx.fromLabel)).append("\",\n");
            json.append("      \"from\": \"").append(escapeJson(tx.from)).append("\",\n");
            json.append("      \"to_label\": \"").append(escapeJson(tx.toLabel)).append("\",\n");
            json.append("      \"to\": \"").append(escapeJson(tx.to)).append("\",\n");
            json.append("      \"amount\": \"").append(String.format("%.8f", tx.amount.longValue() / 100000000.0)).append("\",\n");
            json.append("      \"fee\": \"").append(tx.fee != null ? String.format("%.8f", tx.fee.longValue() / 100000000.0) : "0.00000000").append("\",\n");
            json.append("      \"notes\": \"").append(escapeJson(tx.notes)).append("\"\n");
            json.append("    }");
            if (i < transactionDataList.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}\n");
        
        fos.write(json.toString().getBytes("UTF-8"));
        return true;
    }
    
        private boolean writePdfFile(FileOutputStream fos) throws IOException {
            try {
                // Clean compact PDF with gray color scheme
                StringBuilder pdf = new StringBuilder();
                pdf.append("%PDF-1.4\n");

                // Calculate date range
                Date startDate = null;
                Date endDate = null;
                for (TransactionData tx : transactionDataList) {
                    if (startDate == null || tx.timestamp.before(startDate)) {
                        startDate = tx.timestamp;
                    }
                    if (endDate == null || tx.timestamp.after(endDate)) {
                        endDate = tx.timestamp;
                    }
                }
                
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String dateRange = "";
                if (startDate != null && endDate != null) {
                    dateRange = " from " + dateFormat.format(startDate) + " to " + dateFormat.format(endDate);
                }

                // Calculate totals
                int sentCount = 0;
                int receivedCount = 0;
                Coin totalSent = Coin.ZERO;
                Coin totalReceived = Coin.ZERO;
                Coin totalFees = Coin.ZERO;
                
                for (TransactionData tx : transactionDataList) {
                    if ("send".equals(tx.type)) {
                        sentCount++;
                        totalSent = totalSent.add(tx.amount);
                        if (tx.fee != null) {
                            totalFees = totalFees.add(tx.fee);
                        }
                    } else {
                        receivedCount++;
                        totalReceived = totalReceived.add(tx.amount);
                    }
                }
                
                // Sort transactions by date (oldest first for exports)
                List<TransactionData> sortedTransactions = new ArrayList<>(transactionDataList);
                sortedTransactions.sort((tx1, tx2) -> tx1.timestamp.compareTo(tx2.timestamp));
                
                int transactionsPerPage = 30;
                int totalTransactions = sortedTransactions.size();
                int totalPages = (int) Math.ceil((double) totalTransactions / transactionsPerPage);

            
            // Build PDF with multiple pages - generate content page by page
            int pageCount = totalPages;
            
            // Catalog object
            pdf.append("1 0 obj\n");
            pdf.append("<<\n");
            pdf.append("/Type /Catalog\n");
            pdf.append("/Pages 2 0 R\n");
            pdf.append(">>\n");
            pdf.append("endobj\n");
            
            // Pages object
            pdf.append("2 0 obj\n");
            pdf.append("<<\n");
            pdf.append("/Type /Pages\n");
            pdf.append("/Kids [");
            for (int i = 0; i < pageCount; i++) {
                pdf.append((3 + i * 2)).append(" 0 R");
                if (i < pageCount - 1) pdf.append(" ");
            }
            pdf.append("]\n");
            pdf.append("/Count ").append(pageCount).append("\n");
            pdf.append(">>\n");
            pdf.append("endobj\n");
            
            // Font objects
            int font1Obj = 3 + pageCount * 2;
            int font2Obj = 4 + pageCount * 2;
            
            pdf.append(font1Obj).append(" 0 obj\n");
            pdf.append("<<\n");
            pdf.append("/Type /Font\n");
            pdf.append("/Subtype /Type1\n");
            pdf.append("/BaseFont /Helvetica\n");
            pdf.append(">>\n");
            pdf.append("endobj\n");
            
            pdf.append(font2Obj).append(" 0 obj\n");
            pdf.append("<<\n");
            pdf.append("/Type /Font\n");
            pdf.append("/Subtype /Type1\n");
            pdf.append("/BaseFont /Helvetica-Bold\n");
            pdf.append(">>\n");
            pdf.append("endobj\n");
            
            // Generate content for each page separately
            for (int page = 0; page < pageCount; page++) {
                StringBuilder pageContent = new StringBuilder();
                
                // Page header
                pageContent.append("BT\n");
                pageContent.append("/F1 10 Tf\n");
                pageContent.append("0.4 0.4 0.4 rg\n"); // Gray color
                pageContent.append("50 550 Td\n");
                pageContent.append("(Dogecoin Wallet Report").append(dateRange).append(") Tj\n");
                pageContent.append("ET\n");
                pageContent.append("BT\n");
                pageContent.append("/F1 8 Tf\n");
                pageContent.append("0.5 0.5 0.5 rg\n"); // Gray color
                pageContent.append("500 550 Td\n");
                pageContent.append("(Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append(") Tj\n");
                pageContent.append("ET\n");
                pageContent.append("BT\n");
                pageContent.append("/F1 8 Tf\n");
                pageContent.append("0.4 0.4 0.4 rg\n"); // Gray color
                pageContent.append("50 530 Td\n");
                pageContent.append("(Total transactions: ").append(totalTransactions).append(") Tj\n");
                pageContent.append("200 0 Td\n");
                pageContent.append("(Total amount received: ").append(String.format("%.8f", totalReceived.longValue() / 100000000.0)).append(") Tj\n");
                pageContent.append("200 0 Td\n");
                pageContent.append("(Total amount sent: ").append(String.format("%.8f", totalSent.longValue() / 100000000.0)).append(") Tj\n");
                pageContent.append("200 0 Td\n");
                pageContent.append("(Total send fees: ").append(String.format("%.8f", totalFees.longValue() / 100000000.0)).append(") Tj\n");
                pageContent.append("ET\n");
                pageContent.append("BT\n");
                pageContent.append("/F1 8 Tf\n");
                pageContent.append("0.4 0.4 0.4 rg\n"); // Gray color
                pageContent.append("50 510 Td\n");
                pageContent.append("(Page ").append(page + 1).append(" of ").append(totalPages).append(") Tj\n");
                pageContent.append("ET\n");
                
                // Table header
                pageContent.append("q\n");
                pageContent.append("0.9 0.9 0.9 rg\n"); // Light gray background
                pageContent.append("50 500 m 742 500 l 742 480 l 50 480 l f\n"); // Header background
                pageContent.append("ET\n");
                pageContent.append("q\n");
                pageContent.append("0.7 0.7 0.7 rg\n"); // Border color
                pageContent.append("50 500 m 742 500 l S\n"); // Top border
                pageContent.append("50 480 m 742 480 l S\n"); // Header bottom border
                pageContent.append("ET\n");
                
                // Table header text
                pageContent.append("BT\n");
                pageContent.append("/F1 6 Tf\n");
                pageContent.append("0.4 0.4 0.4 rg\n"); // Gray color
                pageContent.append("55 490 Td\n");
                pageContent.append("(Transaction ID) Tj\n");
                pageContent.append("180 0 Td\n");
                pageContent.append("(Address From) Tj\n");
                pageContent.append("120 0 Td\n");
                pageContent.append("(Address To) Tj\n");
                pageContent.append("120 0 Td\n");
                pageContent.append("(Amount) Tj\n");
                pageContent.append("60 0 Td\n");
                pageContent.append("(Fees) Tj\n");
                pageContent.append("60 0 Td\n");
                pageContent.append("(Timestamp) Tj\n");
                pageContent.append("50 0 Td\n");
                pageContent.append("(Type) Tj\n");
                pageContent.append("ET\n");
                
                // Transaction rows for this page
                int startIndex = page * transactionsPerPage;
                int endIndex = Math.min(startIndex + transactionsPerPage, totalTransactions);
                float currentY = 470;
                float rowHeight = 15;
                
                for (int i = startIndex; i < endIndex; i++) {
                    TransactionData tx = sortedTransactions.get(i);
                    
                    // Alternating row colors
                    if (i % 2 == 0) {
                        pageContent.append("q\n");
                        pageContent.append("0.95 0.95 0.95 rg\n"); // Very light gray
                        pageContent.append("50 ").append(currentY + 1).append(" m 742 ").append(currentY + 1).append(" l 742 ").append(currentY - rowHeight + 1).append(" l 50 ").append(currentY - rowHeight + 1).append(" l f\n");
                        pageContent.append("ET\n");
                    }
                    
                    // Draw row borders
                    pageContent.append("q\n");
                    pageContent.append("0.8 0.8 0.8 rg\n");
                    pageContent.append("50 ").append(currentY - rowHeight + 1).append(" m 742 ").append(currentY - rowHeight + 1).append(" l S\n");
                    pageContent.append("ET\n");
                    
                    // Format transaction data
                    String txid = tx.txid;
                    String type = tx.type;
                    String from = tx.from != null ? tx.from : "";
                    String to = tx.to != null ? tx.to : "";
                    String amount = String.format("%.8f", tx.amount.longValue() / 100000000.0);
                    String fee = tx.fee != null ? String.format("%.8f", tx.fee.longValue() / 100000000.0) : "0.00000000";
                    
                    // Format timestamp with year
                    SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    String timestamp = timestampFormat.format(tx.timestamp);
                    
                    // Escape special characters
                    txid = escapePdfString(txid);
                    type = escapePdfString(type);
                    from = escapePdfString(from);
                    to = escapePdfString(to);
                    amount = escapePdfString(amount);
                    fee = escapePdfString(fee);
                    timestamp = escapePdfString(timestamp);
                    
                    // Transaction row
                    pageContent.append("BT\n");
                    pageContent.append("/F1 5 Tf\n"); // Very small text
                    pageContent.append("0.4 0.4 0.4 rg\n"); // Gray color
                    pageContent.append("55 ").append(currentY - 8).append(" Td\n");
                    
                    // Transaction ID
                    pageContent.append("(").append(txid).append(") Tj\n");
                    pageContent.append("180 0 Td\n");
                    
                    // Address From
                    pageContent.append("(").append(from).append(") Tj\n");
                    pageContent.append("120 0 Td\n");
                    
                    // Address To
                    pageContent.append("(").append(to).append(") Tj\n");
                    pageContent.append("120 0 Td\n");
                    
                    // Amount
                    pageContent.append("(").append(amount).append(") Tj\n");
                    pageContent.append("60 0 Td\n");
                    
                    // Fees
                    pageContent.append("(").append(fee).append(") Tj\n");
                    pageContent.append("60 0 Td\n");
                    
                    // Timestamp
                    pageContent.append("(").append(timestamp).append(") Tj\n");
                    pageContent.append("50 0 Td\n");
                    
                    // Type badge
                    pageContent.append("ET\n"); // End current text object
                    pageContent.append("q\n");
                    if ("send".equals(type)) {
                        pageContent.append("0.8 0.2 0.2 rg\n"); // Red background
                    } else if ("receive".equals(type)) {
                        pageContent.append("0.2 0.8 0.2 rg\n"); // Green background
                    } else if ("internal".equals(type)) {
                        pageContent.append("0.2 0.2 0.8 rg\n"); // Blue background for internal
                    } else {
                        pageContent.append("0.5 0.5 0.5 rg\n"); // Gray background for unknown
                    }
                    // Position badge in Type column
                    float typeX = 55 + 180 + 120 + 120 + 60 + 60 + 50; // Sum of all previous column widths
                    float badgeWidth = 35; // Smaller badge
                    float badgeHeight = 10; // Smaller badge
                    
                    // Draw square rectangle badge - moved down
                    pageContent.append(typeX).append(" ").append(currentY - 10).append(" m ");
                    pageContent.append(typeX + badgeWidth).append(" ").append(currentY - 10).append(" l ");
                    pageContent.append(typeX + badgeWidth).append(" ").append(currentY - 10 + badgeHeight).append(" l ");
                    pageContent.append(typeX).append(" ").append(currentY - 10 + badgeHeight).append(" l f\n"); // Draw square badge
                    pageContent.append("ET\n");
                    pageContent.append("BT\n");
                    pageContent.append("/F1 4 Tf\n");
                    pageContent.append("1 1 1 rg\n"); // White text
                    // Center text in badge - calculate center position based on text length
                    String typeText = type.toUpperCase();
                    float textWidth = typeText.length() * 2.5f; // Approximate character width
                    float centerX = typeX + (badgeWidth / 2) - (textWidth / 2);
                    pageContent.append(centerX).append(" ").append(currentY - 7).append(" Td\n");
                    pageContent.append("(").append(typeText).append(") Tj\n");
                    pageContent.append("ET\n");
                    
                    currentY -= rowHeight;
                }
                
                // Simple footer for this page
                pageContent.append("ET\n");
                pageContent.append("BT\n");
                pageContent.append("/F1 6 Tf\n");
                pageContent.append("0.5 0.5 0.5 rg\n");
                pageContent.append("50 30 Td\n");
                pageContent.append("(Generated by Dogecoin Wallet) Tj\n");
                pageContent.append("ET\n");
                
                // Create page object
                int pageObj = 3 + page * 2;
                int contentObj = 4 + page * 2;
                
                pdf.append(pageObj).append(" 0 obj\n");
                pdf.append("<<\n");
                pdf.append("/Type /Page\n");
                pdf.append("/Parent 2 0 R\n");
                pdf.append("/MediaBox [0 0 792 612]\n"); // Landscape
                pdf.append("/Resources <<\n");
                pdf.append("/Font <<\n");
                pdf.append("/F1 ").append(font1Obj).append(" 0 R\n");
                pdf.append("/F2 ").append(font2Obj).append(" 0 R\n");
                pdf.append(">>\n");
                pdf.append(">>\n");
                pdf.append("/Contents ").append(contentObj).append(" 0 R\n");
                pdf.append(">>\n");
                pdf.append("endobj\n");
                
                // Content stream for this page
                String pageContentStr = pageContent.toString();
                pdf.append(contentObj).append(" 0 obj\n");
                pdf.append("<<\n");
                pdf.append("/Length ").append(pageContentStr.length()).append("\n");
                pdf.append(">>\n");
                pdf.append("stream\n");
                pdf.append(pageContentStr);
                pdf.append("\nendstream\n");
                pdf.append("endobj\n");
                
                log.debug("Generated page " + (page + 1) + " with " + (endIndex - startIndex) + " transactions");
            }
            
            // Xref table
            int totalObjects = 5 + pageCount * 2; // catalog + pages + 2 fonts + (page + content) * pageCount
            pdf.append("xref\n");
            pdf.append("0 ").append(totalObjects).append("\n");
            pdf.append("0000000000 65535 f \n");
            
            // Calculate object positions (simplified)
            int currentPos = 9;
            for (int i = 1; i < totalObjects; i++) {
                pdf.append(String.format("%010d 00000 n \n", currentPos));
                currentPos += 100; // Approximate object size
            }
            
            pdf.append("trailer\n");
            pdf.append("<<\n");
            pdf.append("/Size ").append(totalObjects).append("\n");
            pdf.append("/Root 1 0 R\n");
            pdf.append(">>\n");
            pdf.append("startxref\n");
            pdf.append(pdf.length()).append("\n");
            pdf.append("%%EOF\n");
            
            fos.write(pdf.toString().getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            log.error("Error generating PDF", e);
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        }
    }
    
    private String escapePdfString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("(", "\\(")
                 .replace(")", "\\)")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    private void shareExportFile(File file) {
        try {
            log.debug("Sharing file: {}", file.getAbsolutePath());
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            
            // Set appropriate MIME type based on file extension
            String fileName = file.getName().toLowerCase();
            String mimeType = "application/octet-stream";
            if (fileName.endsWith(".csv")) {
                mimeType = "text/csv";
            } else if (fileName.endsWith(".json")) {
                mimeType = "application/json";
            } else if (fileName.endsWith(".pdf")) {
                mimeType = "application/pdf";
            }
            shareIntent.setType(mimeType);
            
            Uri fileUri;
            try {
                // Try FileProvider first
                fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this, 
                    getPackageName() + ".file_attachment", 
                    file
                );
                log.debug("FileProvider URI created: {}", fileUri);
            } catch (Exception e) {
                log.warn("FileProvider failed, trying fallback: {}", e.getMessage());
                // Fallback to file URI (may not work on newer Android versions)
                fileUri = Uri.fromFile(file);
                log.debug("Fallback file URI created: {}", fileUri);
            }
            
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Dogecoin Transaction Export");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Create chooser with custom title
            Intent chooser = Intent.createChooser(shareIntent, "Share Dogecoin Transaction Export");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            startActivity(chooser);
            log.debug("Share dialog opened successfully");
        } catch (Exception e) {
            log.error("Error sharing file", e);
            Toast.makeText(this, "Error sharing file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            
            // Show file location as fallback
            String message = "File saved to: " + file.getAbsolutePath();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private static class TransactionData {
        String txid;
        String type;
        Date timestamp;
        String fromLabel;
        String from;
        String toLabel;
        String to;
        Coin amount;
        Coin fee;
        String notes;
    }
    
    private enum ExportFormat {
        CSV("csv"),
        JSON("json"),
        PDF("pdf");
        
        private final String extension;
        
        ExportFormat(String extension) {
            this.extension = extension;
        }
        
        public String getExtension() {
            return extension;
        }
    }
    
    private static class DateValueFormatter extends ValueFormatter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
        
        @Override
        public String getFormattedValue(float value) {
            return dateFormat.format(new Date((long) value));
        }
    }
    
    private static class CoinValueFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value) {
            return Coin.valueOf((long) value).toPlainString() + " DOGE";
        }
    }
}
