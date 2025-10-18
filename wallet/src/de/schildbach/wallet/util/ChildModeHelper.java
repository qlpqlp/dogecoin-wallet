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

package de.schildbach.wallet.util;

import android.content.Context;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import android.content.SharedPreferences;
import de.schildbach.wallet.Constants;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.crypto.DeterministicKey;

/**
 * Helper class for Child Mode functionality
 */
public class ChildModeHelper {
    private static final String PREFS_NAME = "child_mode";
    private static final String KEY_CHILD_ADDRESS = "child_address";
    private static final String KEY_CHILD_DERIVED_KEY = "child_derived_key";
    private static final String KEY_CHILD_MODE_ACTIVE = "child_mode_active";
    
    /**
     * Check if child mode is currently active
     */
    public static boolean isChildModeActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_CHILD_MODE_ACTIVE, false);
    }
    
    /**
     * Get the child mode address if active, otherwise return null
     */
    public static String getChildModeAddress(Context context) {
        if (!isChildModeActive(context)) {
            return null;
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CHILD_ADDRESS, null);
    }
    
    /**
     * Get the child mode derived key if active, otherwise return null
     */
    public static String getChildModeDerivedKey(Context context) {
        if (!isChildModeActive(context)) {
            return null;
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CHILD_DERIVED_KEY, null);
    }
    
    /**
     * Get the child mode address as an Address object if active, otherwise return null
     */
    public static Address getChildModeAddressObject(Context context) {
        String addressString = getChildModeAddress(context);
        if (addressString == null) {
            return null;
        }
        
        try {
            return Address.fromString(Constants.NETWORK_PARAMETERS, addressString);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get the child mode derived key as a DeterministicKey object if active, otherwise return null
     */
    public static DeterministicKey getChildModeDerivedKeyObject(Context context) {
        String derivedKeyString = getChildModeDerivedKey(context);
        if (derivedKeyString == null) {
            return null;
        }
        
        try {
            return DeterministicKey.deserializeB58(derivedKeyString, Constants.NETWORK_PARAMETERS);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Set child mode address and derived key
     */
    public static void setChildModeData(Context context, String address, String derivedKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_CHILD_ADDRESS, address);
        editor.putString(KEY_CHILD_DERIVED_KEY, derivedKey);
        editor.putBoolean(KEY_CHILD_MODE_ACTIVE, true);
        editor.apply();
    }
    
    /**
     * Clear child mode data
     */
    public static void clearChildModeData(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_CHILD_ADDRESS);
        editor.remove(KEY_CHILD_DERIVED_KEY);
        editor.putBoolean(KEY_CHILD_MODE_ACTIVE, false);
        editor.apply();
    }
}
