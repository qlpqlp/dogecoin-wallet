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
import de.schildbach.wallet.data.ExcludedAddress;
import de.schildbach.wallet.data.ExcludedAddressDao;
import de.schildbach.wallet.data.DigitalSignature;
import de.schildbach.wallet.data.DigitalSignatureDao;
import de.schildbach.wallet.data.Category;
import de.schildbach.wallet.data.CategoryDao;
import de.schildbach.wallet.data.Product;
import de.schildbach.wallet.data.ProductDao;
import de.schildbach.wallet.data.Check;
import de.schildbach.wallet.data.CheckDao;
import de.schildbach.wallet.data.AuthenticationRequest;
import de.schildbach.wallet.data.AuthenticationRequestDao;
import de.schildbach.wallet.data.AtomicSwap;
import de.schildbach.wallet.data.AtomicSwapDao;

/**
 * @author Andreas Schildbach
 */
@Database(entities = { AddressBookEntry.class, ExcludedAddress.class, DigitalSignature.class, Category.class, Product.class, Check.class, AuthenticationRequest.class, AtomicSwap.class }, version = 8, exportSchema = false)
public abstract class AddressBookDatabase extends RoomDatabase {
    public abstract AddressBookDao addressBookDao();
    public abstract ExcludedAddressDao excludedAddressDao();
    public abstract DigitalSignatureDao digitalSignatureDao();
    public abstract CategoryDao categoryDao();
    public abstract ProductDao productDao();
    public abstract CheckDao checkDao();
    public abstract AuthenticationRequestDao authenticationRequestDao();
    public abstract AtomicSwapDao atomicSwapDao();

    private static final String DATABASE_NAME = "address_book";
    private static AddressBookDatabase INSTANCE;

    public static AddressBookDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AddressBookDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AddressBookDatabase.class, DATABASE_NAME)
                            .fallbackToDestructiveMigration() // Recreate database on any version mismatch
                            .allowMainThreadQueries().build();
                }
            }
        }
        return INSTANCE;
    }

    public static void clearInstance() {
        synchronized (AddressBookDatabase.class) {
            if (INSTANCE != null) {
                try {
                    INSTANCE.close();
                } catch (Exception e) {
                    // Ignore
                }
                INSTANCE = null;
        }
        }
    }

}
