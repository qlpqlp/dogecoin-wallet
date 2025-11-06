package de.schildbach.wallet.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "pos_categories")
public class Category {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name;

    public long timestamp;

    public Category() {
        // Default constructor required by Room
    }

    @Ignore
    public Category(String name) {
        this.name = name;
        this.timestamp = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

