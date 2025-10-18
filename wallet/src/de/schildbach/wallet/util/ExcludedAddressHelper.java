package de.schildbach.wallet.util;

import android.content.Context;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.addressbook.AddressBookDatabase;
import de.schildbach.wallet.data.ExcludedAddress;
import de.schildbach.wallet.data.ExcludedAddressDao;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Helper class for managing excluded addresses and calculating available balance
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class ExcludedAddressHelper {
    private static ExcludedAddressDao excludedAddressDao;
    
    public static void initialize(Context context) {
        excludedAddressDao = AddressBookDatabase.getDatabase(context).excludedAddressDao();
    }
    
    /**
     * Get all excluded addresses
     */
    public static Set<String> getExcludedAddresses() {
        if (excludedAddressDao == null) {
            return new HashSet<>();
        }
        
        List<ExcludedAddress> excludedList = excludedAddressDao.getAllExcludedAddresses();
        Set<String> excludedAddresses = new HashSet<>();
        for (ExcludedAddress excluded : excludedList) {
            excludedAddresses.add(excluded.getAddress());
        }
        return excludedAddresses;
    }
    
    /**
     * Check if an address is excluded from spending
     */
    public static boolean isAddressExcluded(String address) {
        if (excludedAddressDao == null) {
            return false;
        }
        return excludedAddressDao.isAddressExcluded(address) > 0;
    }
    
    /**
     * Check if an address is excluded from spending, considering Child Mode
     * If Child Mode is active and this is the child's address, it should NOT be excluded
     */
    public static boolean isAddressExcluded(String address, Context context) {
        if (excludedAddressDao == null) {
            return false;
        }
        
        // Check if this address is in the excluded list
        boolean isExcluded = excludedAddressDao.isAddressExcluded(address) > 0;
        
        if (isExcluded) {
            // If it's excluded, check if Child Mode is active and this is the child's address
            if (ChildModeHelper.isChildModeActive(context)) {
                String childAddress = ChildModeHelper.getChildModeAddress(context);
                if (childAddress != null && childAddress.equals(address)) {
                    // This is the child's address and Child Mode is active, so it should NOT be excluded
                    return false;
                }
            }
        }
        
        return isExcluded;
    }
    
    /**
     * Add an address to the exclusion list
     */
    public static void excludeAddress(String address, String label) {
        if (excludedAddressDao == null) {
            return;
        }
        
        ExcludedAddress excludedAddress = new ExcludedAddress(address, label);
        excludedAddressDao.insertExcludedAddress(excludedAddress);
    }
    
    /**
     * Remove an address from the exclusion list
     */
    public static void includeAddress(String address) {
        if (excludedAddressDao == null) {
            return;
        }
        
        excludedAddressDao.deleteExcludedAddressByAddress(address);
    }
    
    /**
     * Calculate available balance excluding reserved addresses
     */
    public static Coin getAvailableBalanceExcludingReserved(Wallet wallet) {
        if (wallet == null) {
            return Coin.ZERO;
        }
        
        Set<String> excludedAddresses = getExcludedAddresses();
        if (excludedAddresses.isEmpty()) {
            return wallet.getBalance(Wallet.BalanceType.AVAILABLE);
        }
        
        Coin totalBalance = Coin.ZERO;
        List<TransactionOutput> unspentOutputs = wallet.getUnspents();
        
        for (TransactionOutput output : unspentOutputs) {
            if (output.isAvailableForSpending()) {
                Address outputAddress = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                if (outputAddress != null && !excludedAddresses.contains(outputAddress.toString())) {
                    totalBalance = totalBalance.add(output.getValue());
                }
            }
        }
        
        return totalBalance;
    }
    
    /**
     * Calculate the balance of excluded addresses
     */
    public static Coin getExcludedAddressesBalance(Wallet wallet) {
        if (wallet == null) {
            return Coin.ZERO;
        }
        
        Set<String> excludedAddresses = getExcludedAddresses();
        if (excludedAddresses.isEmpty()) {
            return Coin.ZERO;
        }
        
        Coin excludedBalance = Coin.ZERO;
        List<TransactionOutput> unspentOutputs = wallet.getUnspents();
        
        for (TransactionOutput output : unspentOutputs) {
            if (output.isAvailableForSpending()) {
                Address outputAddress = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
                if (outputAddress != null && excludedAddresses.contains(outputAddress.toString())) {
                    excludedBalance = excludedBalance.add(output.getValue());
                }
            }
        }
        
        return excludedBalance;
    }
}
