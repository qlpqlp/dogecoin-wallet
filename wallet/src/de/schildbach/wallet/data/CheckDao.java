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
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface CheckDao {
    @Query("SELECT * FROM checks ORDER BY createdAt DESC")
    List<Check> getAllChecks();
    
    @Query("SELECT * FROM checks WHERE id = :id")
    Check getCheckById(long id);
    
    @Insert
    long insertCheck(Check check);
    
    @Update
    void updateCheck(Check check);
    
    @Query("DELETE FROM checks WHERE id = :id")
    void deleteCheck(long id);
    
    @Query("SELECT * FROM checks WHERE isSpent = 0 ORDER BY createdAt DESC")
    List<Check> getUnspentChecks();
    
    @Query("SELECT * FROM checks WHERE isSpent = 1 ORDER BY createdAt DESC")
    List<Check> getSpentChecks();
}

