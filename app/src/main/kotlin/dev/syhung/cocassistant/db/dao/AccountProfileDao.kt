package dev.syhung.cocassistant.db.dao

import dev.syhung.cocassistant.db.entity.AccountProfileEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AccountProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccountProfile(profile: AccountProfileEntity)

    @Query("DELETE FROM account_profile WHERE accountId = :accountId")
    suspend fun deleteAccountProfile(accountId: String)

    @Query("SELECT * FROM account_profile")
    fun getAllAccountProfiles(): List<AccountProfileEntity>

    @Query("SELECT * FROM account_profile WHERE accountId = :accountId")
    fun getAccountProfileById(accountId: String): AccountProfileEntity?
}