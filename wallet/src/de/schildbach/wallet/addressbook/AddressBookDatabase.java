/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.addressbook;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import de.schildbach.wallet.data.ExcludedAddress;
import de.schildbach.wallet.data.ExcludedAddressDao;
import de.schildbach.wallet.data.DigitalSignature;
import de.schildbach.wallet.data.DigitalSignatureDao;
import de.schildbach.wallet.data.Category;
import de.schildbach.wallet.data.CategoryDao;
import de.schildbach.wallet.data.Product;
import de.schildbach.wallet.data.ProductDao;

/**
 * @author Andreas Schildbach
 */
@Database(entities = { AddressBookEntry.class, ExcludedAddress.class, DigitalSignature.class, Category.class, Product.class }, version = 5, exportSchema = false)
public abstract class AddressBookDatabase extends RoomDatabase {
    public abstract AddressBookDao addressBookDao();
    public abstract ExcludedAddressDao excludedAddressDao();
    public abstract DigitalSignatureDao digitalSignatureDao();
    public abstract CategoryDao categoryDao();
    public abstract ProductDao productDao();

    private static final String DATABASE_NAME = "address_book";
    private static AddressBookDatabase INSTANCE;

    public static AddressBookDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AddressBookDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AddressBookDatabase.class, DATABASE_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).allowMainThreadQueries().build();
                }
            }
        }
        return INSTANCE;
    }

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE address_book_new (address TEXT NOT NULL, label TEXT, PRIMARY KEY(address))");
            database.execSQL(
                    "INSERT OR IGNORE INTO address_book_new (address, label) SELECT address, label FROM address_book");
            database.execSQL("DROP TABLE address_book");
            database.execSQL("ALTER TABLE address_book_new RENAME TO address_book");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE excluded_addresses (address TEXT NOT NULL, label TEXT, timestamp INTEGER NOT NULL, PRIMARY KEY(address))");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE digital_signatures (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, signature TEXT NOT NULL, address TEXT NOT NULL, type TEXT NOT NULL, content TEXT, fileHash TEXT, tag TEXT, timestamp INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE pos_categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, timestamp INTEGER NOT NULL)");
            database.execSQL(
                    "CREATE TABLE pos_products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryId INTEGER NOT NULL, name TEXT NOT NULL, description TEXT, weight REAL, imagePath TEXT, quantity INTEGER NOT NULL, priceDoge INTEGER NOT NULL, timestamp INTEGER NOT NULL, updatedTimestamp INTEGER NOT NULL, paymentAddress TEXT, requestedQuantity INTEGER NOT NULL, FOREIGN KEY(categoryId) REFERENCES pos_categories(id) ON DELETE CASCADE)");
            database.execSQL(
                    "CREATE INDEX index_pos_products_categoryId ON pos_products(categoryId)");
        }
    };
}
