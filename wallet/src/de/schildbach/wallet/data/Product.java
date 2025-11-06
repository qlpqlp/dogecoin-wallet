package de.schildbach.wallet.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "pos_products",
    foreignKeys = @ForeignKey(
        entity = Category.class,
        parentColumns = "id",
        childColumns = "categoryId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index("categoryId")
)
public class Product {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long categoryId;

    @NonNull
    public String name;

    public String description;
    public Double weight; // Optional weight
    public String imagePath; // Path to product image
    public int quantity; // Current stock quantity
    public long priceDoge; // Price in Dogecoin (in smallest unit)

    public long timestamp;
    public long updatedTimestamp;

    // Unique payment address for this product (generated when purchased)
    public String paymentAddress;
    public int requestedQuantity; // Quantity requested in current payment

    public Product() {
        // Default constructor required by Room
    }

    @Ignore
    public Product(long categoryId, String name, String description, Double weight, 
                   String imagePath, int quantity, long priceDoge) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.weight = weight;
        this.imagePath = imagePath;
        this.quantity = quantity;
        this.priceDoge = priceDoge;
        this.timestamp = System.currentTimeMillis();
        this.updatedTimestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { 
        this.quantity = quantity;
        this.updatedTimestamp = System.currentTimeMillis();
    }

    public long getPriceDoge() { return priceDoge; }
    public void setPriceDoge(long priceDoge) { 
        this.priceDoge = priceDoge;
        this.updatedTimestamp = System.currentTimeMillis();
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getUpdatedTimestamp() { return updatedTimestamp; }
    public void setUpdatedTimestamp(long updatedTimestamp) { this.updatedTimestamp = updatedTimestamp; }

    public String getPaymentAddress() { return paymentAddress; }
    public void setPaymentAddress(String paymentAddress) { this.paymentAddress = paymentAddress; }

    public int getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(int requestedQuantity) { this.requestedQuantity = requestedQuantity; }

    public boolean isAvailable() {
        return quantity == -1 || quantity > 0; // -1 means unlimited
    }
}

