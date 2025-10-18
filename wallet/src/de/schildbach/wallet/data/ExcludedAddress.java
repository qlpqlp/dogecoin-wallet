package de.schildbach.wallet.data;

import androidx.annotation.NonNull;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing an excluded address that should not be used for spending
 */
@Entity(tableName = "excluded_addresses")
public class ExcludedAddress {
    @PrimaryKey
    @NonNull
    public String address;
    
    public String label;
    
    public long timestamp;
    
    public ExcludedAddress(String address, String label) {
        this.address = address;
        this.label = label;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
