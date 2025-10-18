package de.schildbach.wallet.data;

import android.content.ContentValues;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RecurringPaymentDatabase extends SQLiteOpenHelper {
    
    private static final Logger log = LoggerFactory.getLogger(RecurringPaymentDatabase.class);
    
    private static final String DATABASE_NAME = "recurring_payments.db";
    private static final int DATABASE_VERSION = 3;
    
    private static final String TABLE_PAYMENTS = "payments";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_DESTINATION_ADDRESS = "destination_address";
    private static final String COLUMN_REFERENCE = "reference";
    private static final String COLUMN_AMOUNT = "amount";
    private static final String COLUMN_SENDING_ADDRESS_LABEL = "sending_address_label";
    private static final String COLUMN_SENDING_ADDRESS = "sending_address";
    private static final String COLUMN_NEXT_PAYMENT_DATE = "next_payment_date";
    private static final String COLUMN_RECURRING_MONTHLY = "recurring_monthly";
    private static final String COLUMN_ENABLED = "enabled";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_LAST_PAYMENT_DATE = "last_payment_date";
    
    public RecurringPaymentDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_PAYMENTS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_DESTINATION_ADDRESS + " TEXT NOT NULL, " +
                COLUMN_REFERENCE + " TEXT, " +
                COLUMN_AMOUNT + " REAL NOT NULL, " +
                COLUMN_SENDING_ADDRESS_LABEL + " TEXT NOT NULL, " +
                COLUMN_SENDING_ADDRESS + " TEXT NOT NULL, " +
                COLUMN_NEXT_PAYMENT_DATE + " INTEGER NOT NULL, " +
                COLUMN_RECURRING_MONTHLY + " INTEGER NOT NULL, " +
                COLUMN_ENABLED + " INTEGER NOT NULL, " +
                COLUMN_CREATED_AT + " INTEGER NOT NULL, " +
                COLUMN_LAST_PAYMENT_DATE + " INTEGER" +
                ")";
        
        db.execSQL(createTable);
        log.info("Created recurring payments table");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add sending_address column to existing table
            db.execSQL("ALTER TABLE " + TABLE_PAYMENTS + " ADD COLUMN " + COLUMN_SENDING_ADDRESS + " TEXT");
            // Update existing records to have empty sending address
            db.execSQL("UPDATE " + TABLE_PAYMENTS + " SET " + COLUMN_SENDING_ADDRESS + " = '' WHERE " + COLUMN_SENDING_ADDRESS + " IS NULL");
        }
        if (oldVersion < 3) {
            // Add reference column to existing table
            db.execSQL("ALTER TABLE " + TABLE_PAYMENTS + " ADD COLUMN " + COLUMN_REFERENCE + " TEXT");
        }
    }
    
    public long insertPayment(RecurringPayment payment) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_DESTINATION_ADDRESS, payment.getDestinationAddress());
        values.put(COLUMN_REFERENCE, payment.getReference());
        values.put(COLUMN_AMOUNT, payment.getAmount());
        values.put(COLUMN_SENDING_ADDRESS_LABEL, payment.getSendingAddressLabel());
        values.put(COLUMN_SENDING_ADDRESS, payment.getSendingAddress());
        values.put(COLUMN_NEXT_PAYMENT_DATE, payment.getNextPaymentDate().getTime());
        values.put(COLUMN_RECURRING_MONTHLY, payment.isRecurringMonthly() ? 1 : 0);
        values.put(COLUMN_ENABLED, payment.isEnabled() ? 1 : 0);
        values.put(COLUMN_CREATED_AT, payment.getCreatedAt().getTime());
        
        if (payment.getLastPaymentDate() != null) {
            values.put(COLUMN_LAST_PAYMENT_DATE, payment.getLastPaymentDate().getTime());
        }
        
        long id = db.insert(TABLE_PAYMENTS, null, values);
        db.close();
        
        log.info("Inserted recurring payment with ID: {}", id);
        return id;
    }
    
    public List<RecurringPayment> getAllPayments() {
        List<RecurringPayment> payments = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String selectQuery = "SELECT * FROM " + TABLE_PAYMENTS + " ORDER BY " + COLUMN_NEXT_PAYMENT_DATE + " ASC";
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        if (cursor.moveToFirst()) {
            do {
                RecurringPayment payment = cursorToPayment(cursor);
                payments.add(payment);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        
        log.info("Retrieved {} recurring payments", payments.size());
        return payments;
    }
    
    public List<RecurringPayment> getEnabledPayments() {
        List<RecurringPayment> payments = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String selectQuery = "SELECT * FROM " + TABLE_PAYMENTS + 
                " WHERE " + COLUMN_ENABLED + " = 1" +
                " ORDER BY " + COLUMN_NEXT_PAYMENT_DATE + " ASC";
        Cursor cursor = db.rawQuery(selectQuery, null);
        
        if (cursor.moveToFirst()) {
            do {
                RecurringPayment payment = cursorToPayment(cursor);
                payments.add(payment);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        
        log.info("Retrieved {} enabled recurring payments", payments.size());
        return payments;
    }
    
    public RecurringPayment getPayment(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        String selectQuery = "SELECT * FROM " + TABLE_PAYMENTS + " WHERE " + COLUMN_ID + " = ?";
        Cursor cursor = db.rawQuery(selectQuery, new String[]{String.valueOf(id)});
        
        RecurringPayment payment = null;
        if (cursor.moveToFirst()) {
            payment = cursorToPayment(cursor);
        }
        
        cursor.close();
        db.close();
        
        return payment;
    }
    
    public boolean updatePayment(RecurringPayment payment) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_DESTINATION_ADDRESS, payment.getDestinationAddress());
        values.put(COLUMN_REFERENCE, payment.getReference());
        values.put(COLUMN_AMOUNT, payment.getAmount());
        values.put(COLUMN_SENDING_ADDRESS_LABEL, payment.getSendingAddressLabel());
        values.put(COLUMN_SENDING_ADDRESS, payment.getSendingAddress());
        values.put(COLUMN_NEXT_PAYMENT_DATE, payment.getNextPaymentDate().getTime());
        values.put(COLUMN_RECURRING_MONTHLY, payment.isRecurringMonthly() ? 1 : 0);
        values.put(COLUMN_ENABLED, payment.isEnabled() ? 1 : 0);
        
        if (payment.getLastPaymentDate() != null) {
            values.put(COLUMN_LAST_PAYMENT_DATE, payment.getLastPaymentDate().getTime());
        }
        
        int rowsAffected = db.update(TABLE_PAYMENTS, values, COLUMN_ID + " = ?", 
                new String[]{String.valueOf(payment.getId())});
        db.close();
        
        boolean success = rowsAffected > 0;
        log.info("Updated recurring payment {}: {}", payment.getId(), success ? "success" : "failed");
        return success;
    }
    
    public boolean deletePayment(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = db.delete(TABLE_PAYMENTS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        
        boolean success = rowsAffected > 0;
        log.info("Deleted recurring payment {}: {}", id, success ? "success" : "failed");
        return success;
    }
    
    private RecurringPayment cursorToPayment(Cursor cursor) {
        RecurringPayment payment = new RecurringPayment();
        
        payment.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
        payment.setDestinationAddress(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESTINATION_ADDRESS)));
        payment.setReference(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_REFERENCE)));
        payment.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT)));
        payment.setSendingAddressLabel(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDING_ADDRESS_LABEL)));
        payment.setSendingAddress(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDING_ADDRESS)));
        payment.setNextPaymentDate(new Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NEXT_PAYMENT_DATE))));
        payment.setRecurringMonthly(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RECURRING_MONTHLY)) == 1);
        payment.setEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1);
        payment.setCreatedAt(new Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))));
        
        int lastPaymentIndex = cursor.getColumnIndex(COLUMN_LAST_PAYMENT_DATE);
        if (!cursor.isNull(lastPaymentIndex)) {
            payment.setLastPaymentDate(new Date(cursor.getLong(lastPaymentIndex)));
        }
        
        return payment;
    }
}
