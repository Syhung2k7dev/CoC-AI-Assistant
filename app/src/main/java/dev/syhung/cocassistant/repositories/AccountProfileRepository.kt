package dev.syhung.cocassistant.repositories

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.syhung.cocassistant.models.AccountProfile
import java.util.Date

@Dao
interface AccountProfileRepository {
    @Insert
    suspend fun insert(profile: AccountProfile): Long

    @Update
    suspend fun update(profile: AccountProfile)

    @Query("DELETE FROM account_profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM account_profiles WHERE accountName = :accountName")
    suspend fun findByAccountName(accountName: String): AccountProfile?

    @Query("SELECT * FROM account_profiles WHERE supercellId = :supercellId")
    suspend fun findBySupercellId(supercellId: String): AccountProfile?

    @Query("SELECT * FROM account_profiles WHERE isLogged = 1 ORDER BY creationDate DESC")
    suspend fun findAllActive(): List<AccountProfile>

    @Query("SELECT * FROM account_profiles WHERE id = :id")
    suspend fun findByID(id: Long): AccountProfile?

    @Query("INSERT INTO account_profiles (accountName, supercellId, isLogged, creationDate) VALUES (:name, :id, 1, :now)")
    suspend fun createFromSupercellLogin(name: String, id: String, now: Date): Long

    @Query("SELECT * FROM account_profiles WHERE isDefaultAccount = 1 AND isLogged = 1 LIMIT 1")
    suspend fun findDefault(): AccountProfile?

    @Query("UPDATE account_profiles SET isLogged = :isLoggedIn WHERE id != :defaultId")
    suspend fun updateAllLogged(isLoggedIn: Boolean, defaultId: Long)

    @Query("SELECT * FROM account_profiles WHERE currentScene IS NOT NULL ORDER BY creationDate DESC LIMIT 1")
    suspend fun findCurrentUser(): AccountProfile?
}
