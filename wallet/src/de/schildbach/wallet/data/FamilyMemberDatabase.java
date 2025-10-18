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

package de.schildbach.wallet.data;

import android.content.ContentValues;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.List;

/**
 * Database helper for Family Member data
 */
public class FamilyMemberDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "family_members.db";
    private static final int DATABASE_VERSION = 1;

    // Table name
    private static final String TABLE_FAMILY_MEMBERS = "family_members";

    // Column names
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_DERIVED_KEY = "derived_key";
    private static final String COLUMN_ADDRESS = "address";
    private static final String COLUMN_BALANCE = "balance";
    private static final String COLUMN_CREATED_TIME = "created_time";
    private static final String COLUMN_IS_ACTIVE = "is_active";
    
    private Context context;

    public FamilyMemberDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_FAMILY_MEMBERS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT NOT NULL, " +
                COLUMN_DERIVED_KEY + " TEXT NOT NULL, " +
                COLUMN_ADDRESS + " TEXT NOT NULL, " +
                COLUMN_BALANCE + " INTEGER NOT NULL, " +
                COLUMN_CREATED_TIME + " INTEGER NOT NULL, " +
                COLUMN_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 0" +
                ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrades here if needed
    }

    public long insertFamilyMember(FamilyMember member) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, member.getName());
        values.put(COLUMN_DERIVED_KEY, member.getDerivedKey());
        values.put(COLUMN_ADDRESS, member.getAddress());
        values.put(COLUMN_BALANCE, member.getBalance().value);
        values.put(COLUMN_CREATED_TIME, member.getCreatedTime());
        values.put(COLUMN_IS_ACTIVE, member.isActive() ? 1 : 0);

        long id = db.insert(TABLE_FAMILY_MEMBERS, null, values);
        db.close();
        return id;
    }

    public List<FamilyMember> getAllFamilyMembers() {
        List<FamilyMember> members = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_FAMILY_MEMBERS + " ORDER BY " + COLUMN_CREATED_TIME + " DESC";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                FamilyMember member = cursorToFamilyMember(cursor);
                members.add(member);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return members;
    }

    public FamilyMember getFamilyMember(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAMILY_MEMBERS, null, COLUMN_ID + "=?", 
                new String[]{String.valueOf(id)}, null, null, null);
        
        FamilyMember member = null;
        if (cursor.moveToFirst()) {
            member = cursorToFamilyMember(cursor);
        }
        cursor.close();
        db.close();
        return member;
    }

    public int updateFamilyMember(FamilyMember member) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, member.getName());
        values.put(COLUMN_DERIVED_KEY, member.getDerivedKey());
        values.put(COLUMN_ADDRESS, member.getAddress());
        values.put(COLUMN_BALANCE, member.getBalance().value);
        values.put(COLUMN_CREATED_TIME, member.getCreatedTime());
        values.put(COLUMN_IS_ACTIVE, member.isActive() ? 1 : 0);

        int result = db.update(TABLE_FAMILY_MEMBERS, values, COLUMN_ID + "=?", 
                new String[]{String.valueOf(member.getId())});
        db.close();
        return result;
    }

    public int deleteFamilyMember(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = db.delete(TABLE_FAMILY_MEMBERS, COLUMN_ID + "=?", 
                new String[]{String.valueOf(id)});
        db.close();
        return result;
    }

    public void setActiveFamilyMember(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        // First, deactivate all members
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_ACTIVE, 0);
        db.update(TABLE_FAMILY_MEMBERS, values, null, null);
        
        // Then activate the selected member
        values = new ContentValues();
        values.put(COLUMN_IS_ACTIVE, 1);
        db.update(TABLE_FAMILY_MEMBERS, values, COLUMN_ID + "=?", 
                new String[]{String.valueOf(id)});
        db.close();
    }

    public FamilyMember getActiveFamilyMember() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAMILY_MEMBERS, null, COLUMN_IS_ACTIVE + "=1", 
                null, null, null, null);
        
        FamilyMember member = null;
        if (cursor.moveToFirst()) {
            member = cursorToFamilyMember(cursor);
        }
        cursor.close();
        db.close();
        return member;
    }
    
    /**
     * Store child mode address for wallet override
     */
    public void setChildModeAddress(String address) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("child_mode", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("child_address", address);
        editor.apply();
    }
    
    /**
     * Get child mode address
     */
    public String getChildModeAddress() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("child_mode", android.content.Context.MODE_PRIVATE);
        return prefs.getString("child_address", null);
    }
    
    /**
     * Store child mode derived key for wallet override
     */
    public void setChildModeDerivedKey(String derivedKey) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("child_mode", android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("child_derived_key", derivedKey);
        editor.apply();
    }
    
    /**
     * Get child mode derived key
     */
    public String getChildModeDerivedKey() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("child_mode", android.content.Context.MODE_PRIVATE);
        return prefs.getString("child_derived_key", null);
    }
    
    /**
     * Check if child mode is active
     */
    public boolean isChildModeActive() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("child_mode", android.content.Context.MODE_PRIVATE);
        return prefs.getBoolean("child_mode_active", false);
    }

    private FamilyMember cursorToFamilyMember(Cursor cursor) {
        FamilyMember member = new FamilyMember();
        member.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
        member.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)));
        member.setDerivedKey(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DERIVED_KEY)));
        member.setAddress(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS)));
        member.setBalance(Coin.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_BALANCE))));
        member.setCreatedTime(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_TIME)));
        member.setActive(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ACTIVE)) == 1);
        return member;
    }
}
