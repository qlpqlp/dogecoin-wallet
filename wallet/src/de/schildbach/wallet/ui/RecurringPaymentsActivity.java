package de.schildbach.wallet.ui;

import android.app.AlertDialog;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.data.RecurringPayment;
import de.schildbach.wallet.data.RecurringPaymentDatabase;
import de.schildbach.wallet.service.RecurringPaymentsService;
import org.bitcoinj.core.Address;

public class RecurringPaymentsActivity extends AbstractWalletActivity {
    
    private static final Logger log = LoggerFactory.getLogger(RecurringPaymentsActivity.class);
    
    private RecyclerView recyclerPayments;
    private LinearLayout layoutEmptyState;
    private Button btnAddPayment;
    private RecurringPaymentsAdapter adapter;
    private RecurringPaymentDatabase database;
    private AddressBookDatabase addressBookDatabase;
    private List<RecurringPayment> payments = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recurring_payments);
        
        // Initialize databases
        database = new RecurringPaymentDatabase(this);
        addressBookDatabase = AddressBookDatabase.getDatabase(this);
        
        // Start recurring payments service
        RecurringPaymentsService.schedule((WalletApplication) getApplication());
        
        // Initialize views
        recyclerPayments = findViewById(R.id.recycler_payments);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        btnAddPayment = findViewById(R.id.btn_add_payment);
        
        // Setup RecyclerView
        recyclerPayments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecurringPaymentsAdapter();
        recyclerPayments.setAdapter(adapter);
        
        // Setup click listeners
        btnAddPayment.setOnClickListener(v -> showSchedulePaymentDialog());
        
        // Load payments
        loadPayments();
    }
    
    private void loadPayments() {
        payments = database.getAllPayments();
        adapter.notifyDataSetChanged();
        
        if (payments.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            recyclerPayments.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            recyclerPayments.setVisibility(View.VISIBLE);
        }
    }
    
    private void showSchedulePaymentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_schedule_payment, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Get dialog views
        Spinner spinnerDestinationAddress = dialogView.findViewById(R.id.spinner_destination_address);
        EditText editReference = dialogView.findViewById(R.id.edit_reference);
        EditText editAmount = dialogView.findViewById(R.id.edit_amount);
        Button btnSelectDate = dialogView.findViewById(R.id.btn_select_date);
        Button btnSelectTime = dialogView.findViewById(R.id.btn_select_time);
        CheckBox checkboxRecurring = dialogView.findViewById(R.id.checkbox_recurring);
        Button btnAddDestinationAddress = dialogView.findViewById(R.id.btn_add_destination_address);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        // Setup destination address spinner (from address book)
        setupDestinationAddressSpinner(spinnerDestinationAddress);
        
        // Setup date and time pickers
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 1); // Default to 1 minute from now for testing
        updateDateTimeButtons(btnSelectDate, btnSelectTime, calendar.getTime());
        
        btnSelectDate.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    updateDateTimeButtons(btnSelectDate, btnSelectTime, calendar.getTime());
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
            datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
            datePicker.show();
        });
        
        btnSelectTime.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateDateTimeButtons(btnSelectDate, btnSelectTime, calendar.getTime());
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true); // 24-hour format
            timePicker.show();
        });
        
        // Setup add address button
        btnAddDestinationAddress.setOnClickListener(v -> showAddAddressDialog(spinnerDestinationAddress));
        
        // Setup save button
        btnSave.setOnClickListener(v -> {
            String amountText = editAmount.getText().toString().trim();
            String reference = editReference.getText().toString().trim();
            
            // Get selected destination address
            int destinationIndex = spinnerDestinationAddress.getSelectedItemPosition();
            if (destinationIndex < 0 || destinationIndex >= addressBookEntries.size()) {
                Toast.makeText(this, "Please select a destination address", Toast.LENGTH_SHORT).show();
                return;
            }
            String destinationAddress = addressBookEntries.get(destinationIndex).getAddress();
            
            if (TextUtils.isEmpty(amountText)) {
                Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                double amount = Double.parseDouble(amountText);
                if (amount <= 0) {
                    Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Create recurring payment
                RecurringPayment payment = new RecurringPayment();
                payment.setDestinationAddress(destinationAddress);
                payment.setReference(reference.isEmpty() ? null : reference);
                payment.setAmount(amount);
                
                // Wallet will automatically select the sending address when executing the payment
                payment.setSendingAddressLabel("Auto-selected by wallet");
                payment.setSendingAddress("");
                
                payment.setNextPaymentDate(calendar.getTime());
                payment.setRecurringMonthly(checkboxRecurring.isChecked());
                payment.setEnabled(true);
                
                // Save to database
                long id = database.insertPayment(payment);
                if (id > 0) {
                    payment.setId(id);
                    payments.add(payment);
                    adapter.notifyItemInserted(payments.size() - 1);
                    
                    if (payments.size() == 1) {
                        loadPayments(); // Refresh to hide empty state
                    }
                    
                    Toast.makeText(this, "Recurring payment scheduled", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "Failed to save payment", Toast.LENGTH_SHORT).show();
                }
                
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount format", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Setup cancel button
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private List<AddressBookEntry> addressBookEntries = new ArrayList<>();
    
    private void setupDestinationAddressSpinner(Spinner spinner) {
        // Load addresses from address book (for destination)
        addressBookDatabase.addressBookDao().getAll().observe(this, entries -> {
            addressBookEntries = entries != null ? entries : new ArrayList<>();
            List<String> addressLabels = new ArrayList<>();
            
            if (!addressBookEntries.isEmpty()) {
                for (AddressBookEntry entry : addressBookEntries) {
                    String label = entry.getLabel();
                    if (label != null && !label.trim().isEmpty()) {
                        addressLabels.add(label + " (" + shortenAddress(entry.getAddress()) + ")");
                    } else {
                        addressLabels.add(shortenAddress(entry.getAddress()));
                    }
                }
            } else {
                addressLabels.add("No addresses in address book");
            }
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, addressLabels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        });
    }
    
    
    private String shortenAddress(String address) {
        if (address.length() > 12) {
            return address.substring(0, 6) + "..." + address.substring(address.length() - 6);
        }
        return address;
    }
    
    private void showAddAddressDialog(Spinner spinner) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Create a layout with two input fields
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        EditText editAddress = new EditText(this);
        editAddress.setHint("Dogecoin Address");
        editAddress.setPadding(16, 16, 16, 16);
        editAddress.setTextSize(14);
        
        EditText editLabel = new EditText(this);
        editLabel.setHint("Address Label (optional)");
        editLabel.setPadding(16, 16, 16, 16);
        editLabel.setTextSize(14);
        
        layout.addView(editAddress);
        layout.addView(editLabel);
        
        builder.setTitle("Add New Address")
               .setView(layout)
               .setPositiveButton("Add", (dialog, which) -> {
                   String address = editAddress.getText().toString().trim();
                   String label = editLabel.getText().toString().trim();
                   
                   if (!TextUtils.isEmpty(address)) {
                       try {
                           // Validate address format
                           Address.fromString(Constants.NETWORK_PARAMETERS, address);
                           
                           // Add to address book
                           AddressBookEntry entry = new AddressBookEntry(address, label.isEmpty() ? null : label);
                           addressBookDatabase.addressBookDao().insertOrUpdate(entry);
                           
                           Toast.makeText(this, "Address added successfully", Toast.LENGTH_SHORT).show();
                           
                           // Refresh the spinner
                           setupDestinationAddressSpinner(spinner);
                           
                       } catch (Exception e) {
                           Toast.makeText(this, "Invalid Dogecoin address format", Toast.LENGTH_SHORT).show();
                           log.error("Invalid address format: {}", address, e);
                       }
                   } else {
                       Toast.makeText(this, "Please enter a Dogecoin address", Toast.LENGTH_SHORT).show();
                   }
               })
               .setNegativeButton(R.string.common_cancel, null);
        
        builder.create().show();
    }
    
    private void editPayment(RecurringPayment payment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Payment");
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_schedule_payment, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Get dialog views
        Spinner spinnerDestinationAddress = dialogView.findViewById(R.id.spinner_destination_address);
        EditText editReference = dialogView.findViewById(R.id.edit_reference);
        EditText editAmount = dialogView.findViewById(R.id.edit_amount);
        Button btnSelectDate = dialogView.findViewById(R.id.btn_select_date);
        Button btnSelectTime = dialogView.findViewById(R.id.btn_select_time);
        CheckBox checkboxRecurring = dialogView.findViewById(R.id.checkbox_recurring);
        Button btnAddDestinationAddress = dialogView.findViewById(R.id.btn_add_destination_address);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        // Pre-populate fields with existing payment data
        editReference.setText(payment.getReference() != null ? payment.getReference() : "");
        editAmount.setText(String.valueOf(payment.getAmount()));
        checkboxRecurring.setChecked(payment.isRecurringMonthly());
        
        // Setup destination address spinner with current selection
        setupDestinationAddressSpinnerWithSelection(spinnerDestinationAddress, payment.getDestinationAddress());
        
        // Setup date and time pickers
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(payment.getNextPaymentDate());
        updateDateTimeButtons(btnSelectDate, btnSelectTime, calendar.getTime());
        
        btnSelectDate.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                updateDateTimeButtons(btnSelectDate, btnSelectTime, calendar.getTime());
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), 
                calendar.get(Calendar.DAY_OF_MONTH));
            datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
            datePicker.show();
        });
        
        btnSelectTime.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    updateDateTimeButtons(btnSelectDate, btnSelectTime, calendar.getTime());
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true); // 24-hour format
            timePicker.show();
        });
        
        // Setup add address button
        btnAddDestinationAddress.setOnClickListener(v -> showAddAddressDialog(spinnerDestinationAddress));
        
        // Setup save button
        btnSave.setOnClickListener(v -> {
            String amountText = editAmount.getText().toString().trim();
            String reference = editReference.getText().toString().trim();
            
            // Get selected destination address
            int destinationIndex = spinnerDestinationAddress.getSelectedItemPosition();
            if (destinationIndex < 0 || destinationIndex >= addressBookEntries.size()) {
                Toast.makeText(this, "Please select a destination address", Toast.LENGTH_SHORT).show();
                return;
            }
            String destinationAddress = addressBookEntries.get(destinationIndex).getAddress();
            
            if (TextUtils.isEmpty(amountText)) {
                Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                double amount = Double.parseDouble(amountText);
                if (amount <= 0) {
                    Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Update payment
                payment.setDestinationAddress(destinationAddress);
                payment.setReference(reference.isEmpty() ? null : reference);
                payment.setAmount(amount);
                payment.setNextPaymentDate(calendar.getTime());
                payment.setRecurringMonthly(checkboxRecurring.isChecked());
                
                // Update in database
                if (database.updatePayment(payment)) {
                    Toast.makeText(this, "Payment updated successfully", Toast.LENGTH_SHORT).show();
                    loadPayments();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "Failed to update payment", Toast.LENGTH_SHORT).show();
                }
                
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount format", Toast.LENGTH_SHORT).show();
            }
        });
        
        // Setup cancel button
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void setupDestinationAddressSpinnerWithSelection(Spinner spinner, String selectedAddress) {
        // Load addresses from address book and select the current one
        addressBookDatabase.addressBookDao().getAll().observe(this, entries -> {
            addressBookEntries = entries != null ? entries : new ArrayList<>();
            List<String> addressLabels = new ArrayList<>();
            int selectedIndex = 0;
            
            if (!addressBookEntries.isEmpty()) {
                for (int i = 0; i < addressBookEntries.size(); i++) {
                    AddressBookEntry entry = addressBookEntries.get(i);
                    String label = entry.getLabel();
                    if (label != null && !label.trim().isEmpty()) {
                        addressLabels.add(label + " (" + shortenAddress(entry.getAddress()) + ")");
                    } else {
                        addressLabels.add(shortenAddress(entry.getAddress()));
                    }
                    
                    // Check if this is the selected address
                    if (entry.getAddress().equals(selectedAddress)) {
                        selectedIndex = i;
                    }
                }
            } else {
                addressLabels.add("No addresses in address book");
            }
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, addressLabels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setSelection(selectedIndex);
        });
    }
    
    private void updateDateButton(Button button, Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        button.setText(sdf.format(date));
    }
    
    private void updateDateTimeButtons(Button dateButton, Button timeButton, Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        dateButton.setText(dateFormat.format(date));
        timeButton.setText(timeFormat.format(date));
    }
    
    private class RecurringPaymentsAdapter extends RecyclerView.Adapter<RecurringPaymentsAdapter.PaymentViewHolder> {
        
        @NonNull
        @Override
        public PaymentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recurring_payment, parent, false);
            return new PaymentViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull PaymentViewHolder holder, int position) {
            RecurringPayment payment = payments.get(position);
            holder.bind(payment);
        }
        
        @Override
        public int getItemCount() {
            return payments.size();
        }
        
        class PaymentViewHolder extends RecyclerView.ViewHolder {
            private TextView textAmount;
            private TextView textFrequency;
            private TextView textDestinationAddress;
            private TextView textNextPayment;
            private LinearLayout layoutReference;
            private TextView textReference;
            private Switch switchEnabled;
            private Button btnEdit;
            private Button btnDelete;
            
            public PaymentViewHolder(@NonNull View itemView) {
                super(itemView);
                textAmount = itemView.findViewById(R.id.text_amount);
                textFrequency = itemView.findViewById(R.id.text_frequency);
                textDestinationAddress = itemView.findViewById(R.id.text_destination_address);
                textNextPayment = itemView.findViewById(R.id.text_next_payment);
                layoutReference = itemView.findViewById(R.id.layout_reference);
                textReference = itemView.findViewById(R.id.text_reference);
                switchEnabled = itemView.findViewById(R.id.switch_enabled);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
            
            public void bind(RecurringPayment payment) {
                textAmount.setText(String.format(Locale.getDefault(), "%.2f DOGE", payment.getAmount()));
                textFrequency.setText(payment.isRecurringMonthly() ? "Monthly" : "One-time");
                textDestinationAddress.setText("To: " + shortenAddress(payment.getDestinationAddress()));
                textNextPayment.setText("Next: " + formatDate(payment.getNextPaymentDate()));
                
                // Show reference if it exists
                if (payment.getReference() != null && !payment.getReference().trim().isEmpty()) {
                    layoutReference.setVisibility(View.VISIBLE);
                    textReference.setText("Ref: " + payment.getReference());
                } else {
                    layoutReference.setVisibility(View.GONE);
                }
                
                // Clear any existing listener first to prevent multiple listeners
                switchEnabled.setOnCheckedChangeListener(null);
                switchEnabled.setChecked(payment.isEnabled());
                
                // Setup switch listener
                switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    payment.setEnabled(isChecked);
                    database.updatePayment(payment);
                    Toast.makeText(RecurringPaymentsActivity.this, 
                        isChecked ? "Payment enabled" : "Payment disabled", 
                        Toast.LENGTH_SHORT).show();
                });
                
                // Setup edit button
                btnEdit.setOnClickListener(v -> {
                    editPayment(payment);
                });
                
                // Setup delete button
                btnDelete.setOnClickListener(v -> {
                    new AlertDialog.Builder(RecurringPaymentsActivity.this)
                        .setTitle("Delete Payment")
                        .setMessage(R.string.recurring_payments_confirm_delete)
                        .setPositiveButton("Delete", (dialog, which) -> {
                            int position = getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                RecurringPayment paymentToDelete = payments.get(position);
                                if (database.deletePayment(paymentToDelete.getId())) {
                                    payments.remove(position);
                                    notifyItemRemoved(position);
                                    
                                    if (payments.isEmpty()) {
                                        loadPayments(); // Refresh to show empty state
                                    }
                                    
                                    Toast.makeText(RecurringPaymentsActivity.this, "Payment deleted", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton(R.string.common_cancel, null)
                        .show();
                });
            }
            
            private String shortenAddress(String address) {
                if (address.length() > 12) {
                    return address.substring(0, 6) + "..." + address.substring(address.length() - 6);
                }
                return address;
            }
            
            private String formatDate(Date date) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                return sdf.format(date);
            }
        }
    }
}
