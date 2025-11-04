package de.schildbach.wallet.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for digital signatures
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
@Dao
public interface DigitalSignatureDao {
    @Query("SELECT * FROM digital_signatures ORDER BY timestamp DESC")
    List<DigitalSignature> getAllSignatures();
    
    @Query("SELECT * FROM digital_signatures WHERE id = :id LIMIT 1")
    DigitalSignature getSignature(long id);
    
    @Query("SELECT * FROM digital_signatures WHERE type = :type ORDER BY timestamp DESC")
    List<DigitalSignature> getSignaturesByType(String type);
    
    @Query("SELECT * FROM digital_signatures WHERE tag LIKE '%' || :tagFilter || '%' ORDER BY timestamp DESC")
    List<DigitalSignature> getSignaturesByTag(String tagFilter);
    
    @Insert
    long insertSignature(DigitalSignature signature);
    
    @Update
    void updateSignature(DigitalSignature signature);
    
    @Delete
    void deleteSignature(DigitalSignature signature);
    
    @Query("DELETE FROM digital_signatures WHERE id = :id")
    void deleteSignatureById(long id);
}

