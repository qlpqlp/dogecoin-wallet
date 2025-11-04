package de.schildbach.wallet.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CategoryDao {
    @Insert
    long insertCategory(Category category);

    @Update
    void updateCategory(Category category);

    @Delete
    void deleteCategory(Category category);

    @Query("SELECT * FROM pos_categories ORDER BY name ASC")
    List<Category> getAllCategories();

    @Query("SELECT * FROM pos_categories WHERE id = :id LIMIT 1")
    Category getCategoryById(long id);

    @Query("SELECT * FROM pos_categories WHERE name = :name LIMIT 1")
    Category getCategoryByName(String name);
}

