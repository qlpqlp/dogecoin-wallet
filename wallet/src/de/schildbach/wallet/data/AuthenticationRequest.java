package de.schildbach.wallet.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity representing an authentication request to be signed by a Dogecoin address.
 *
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
@Entity(tableName = "authentication_requests")
public class AuthenticationRequest {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String address; // The Dogecoin address used for signing

    @NonNull
    public String message; // The message to be signed

    public String label; // Optional user-defined label for the request

    @NonNull
    public long timestamp; // When the request was created

    public AuthenticationRequest() {
        this.timestamp = System.currentTimeMillis();
    }

    public AuthenticationRequest(@NonNull String address, @NonNull String message, String label) {
        this.address = address;
        this.message = message;
        this.label = label;
        this.timestamp = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getAddress() {
        return address;
    }

    public void setAddress(@NonNull String address) {
        this.address = address;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    public void setMessage(@NonNull String message) {
        this.message = message;
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

    @Override
    public String toString() {
        return (label != null && !label.isEmpty() ? label + " - " : "") + message;
    }
}

