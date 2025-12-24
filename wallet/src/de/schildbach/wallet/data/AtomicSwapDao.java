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

package de.schildbach.wallet.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for Atomic Swap operations
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
@Dao
public interface AtomicSwapDao {
    @Insert
    long insertAtomicSwap(AtomicSwap swap);
    
    @Update
    void updateAtomicSwap(AtomicSwap swap);
    
    @Delete
    void deleteAtomicSwap(AtomicSwap swap);
    
    @Query("SELECT * FROM atomic_swaps ORDER BY createdAt DESC")
    List<AtomicSwap> getAllSwaps();
    
    @Query("SELECT * FROM atomic_swaps WHERE id = :id LIMIT 1")
    AtomicSwap getSwapById(long id);
    
    @Query("SELECT * FROM atomic_swaps WHERE status = :status ORDER BY createdAt DESC")
    List<AtomicSwap> getSwapsByStatus(String status);
    
    @Query("SELECT * FROM atomic_swaps WHERE fromCurrency = :fromCurrency AND toCurrency = :toCurrency ORDER BY createdAt DESC")
    List<AtomicSwap> getSwapsByCurrencyPair(String fromCurrency, String toCurrency);
    
    @Query("SELECT * FROM atomic_swaps WHERE secretHash = :secretHash LIMIT 1")
    AtomicSwap getSwapBySecretHash(String secretHash);
    
    @Query("SELECT * FROM atomic_swaps WHERE dogecoinContractAddress = :address LIMIT 1")
    AtomicSwap getSwapByDogecoinContractAddress(String address);
    
    @Query("SELECT * FROM atomic_swaps WHERE counterpartyContractAddress = :address LIMIT 1")
    AtomicSwap getSwapByCounterpartyContractAddress(String address);
    
    @Query("SELECT * FROM atomic_swaps WHERE status IN ('PENDING', 'CONTRACT_CREATED', 'COUNTERPARTY_CONTRACT_CREATED', 'SECRET_REVEALED') ORDER BY createdAt DESC")
    List<AtomicSwap> getActiveSwaps();
    
    @Query("SELECT * FROM atomic_swaps WHERE expiresAt > 0 AND expiresAt < :currentTime AND status NOT IN ('COMPLETED', 'REFUNDED', 'FAILED')")
    List<AtomicSwap> getExpiredSwaps(long currentTime);
    
    @Query("UPDATE atomic_swaps SET status = :status WHERE id = :id")
    void updateSwapStatus(long id, String status);
    
    @Query("UPDATE atomic_swaps SET secret = :secret, secretRevealedAt = :timestamp WHERE id = :id")
    void updateSwapSecret(long id, String secret, long timestamp);
    
    @Query("UPDATE atomic_swaps SET counterpartyContractAddress = :address, counterpartyContractCreatedAt = :timestamp, status = :status WHERE id = :id")
    void updateCounterpartyContract(long id, String address, long timestamp, String status);
}

