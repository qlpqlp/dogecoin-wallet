package de.schildbach.wallet.data;

import androidx.room.Dao;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for excluded addresses
 */
@Dao
public interface ExcludedAddressDao {
    @Query("SELECT * FROM excluded_addresses ORDER BY timestamp DESC")
    List<ExcludedAddress> getAllExcludedAddresses();
    
    @Query("SELECT * FROM excluded_addresses WHERE address = :address LIMIT 1")
    ExcludedAddress getExcludedAddress(String address);
    
    @Query("SELECT COUNT(*) FROM excluded_addresses WHERE address = :address")
    int isAddressExcluded(String address);
    
    @Insert
    void insertExcludedAddress(ExcludedAddress excludedAddress);
    
    @Update
    void updateExcludedAddress(ExcludedAddress excludedAddress);
    
    @Delete
    void deleteExcludedAddress(ExcludedAddress excludedAddress);
    
    @Query("DELETE FROM excluded_addresses WHERE address = :address")
    void deleteExcludedAddressByAddress(String address);
}
