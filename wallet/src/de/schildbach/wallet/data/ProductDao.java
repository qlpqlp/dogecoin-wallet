package de.schildbach.wallet.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProductDao {
    @Insert
    long insertProduct(Product product);

    @Update
    void updateProduct(Product product);

    @Delete
    void deleteProduct(Product product);

    @Query("SELECT * FROM pos_products ORDER BY timestamp DESC")
    List<Product> getAllProducts();

    @Query("SELECT * FROM pos_products WHERE categoryId = :categoryId AND (quantity = -1 OR quantity > 0) ORDER BY name ASC")
    List<Product> getAvailableProductsByCategory(long categoryId);

    @Query("SELECT * FROM pos_products WHERE categoryId = :categoryId ORDER BY name ASC")
    List<Product> getAllProductsByCategory(long categoryId);

    @Query("SELECT * FROM pos_products WHERE id = :id LIMIT 1")
    Product getProductById(long id);

    @Query("SELECT * FROM pos_products WHERE paymentAddress = :address LIMIT 1")
    Product getProductByPaymentAddress(String address);

    @Query("UPDATE pos_products SET quantity = quantity - :deducted WHERE id = :id")
    void deductQuantity(long id, int deducted);

    @Query("SELECT DISTINCT categoryId FROM pos_products WHERE quantity = -1 OR quantity > 0")
    List<Long> getCategoriesWithAvailableProducts();
}

