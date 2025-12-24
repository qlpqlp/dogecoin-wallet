package de.schildbach.wallet.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for authentication requests
 *
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
@Dao
public interface AuthenticationRequestDao {
    @Query("SELECT * FROM authentication_requests ORDER BY timestamp DESC")
    List<AuthenticationRequest> getAllAuthenticationRequests();

    @Query("SELECT * FROM authentication_requests WHERE id = :id LIMIT 1")
    AuthenticationRequest getAuthenticationRequest(long id);

    @Insert
    long insertAuthenticationRequest(AuthenticationRequest request);

    @Update
    void updateAuthenticationRequest(AuthenticationRequest request);

    @Delete
    void deleteAuthenticationRequest(AuthenticationRequest request);

    @Query("DELETE FROM authentication_requests WHERE id = :id")
    void deleteAuthenticationRequestById(long id);
}

