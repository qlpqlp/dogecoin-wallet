package de.schildbach.wallet.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entity representing a digital signature record
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
@Entity(tableName = "digital_signatures")
public class DigitalSignature {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @NonNull
    public String signature;
    
    @NonNull
    public String address;
    
    @NonNull
    public String type; // "text", "file", or "photo"
    
    public String content; // The text, file path, or photo path that was signed
    public String fileHash; // SHA256 hash for file/photo signatures
    
    public String tag; // User-defined tag/label
    
    @NonNull
    public long timestamp; // When the signature was created
    
    public DigitalSignature() {
        this.timestamp = System.currentTimeMillis();
    }
    
    @Ignore
    public DigitalSignature(@NonNull String signature, @NonNull String address, @NonNull String type) {
        this.signature = signature;
        this.address = address;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    @NonNull
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(@NonNull String signature) {
        this.signature = signature;
    }
    
    @NonNull
    public String getAddress() {
        return address;
    }
    
    public void setAddress(@NonNull String address) {
        this.address = address;
    }
    
    @NonNull
    public String getType() {
        return type;
    }
    
    public void setType(@NonNull String type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getFileHash() {
        return fileHash;
    }
    
    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    @NonNull
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

