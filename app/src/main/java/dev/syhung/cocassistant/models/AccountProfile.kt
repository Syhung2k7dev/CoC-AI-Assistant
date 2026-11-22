package dev.syhung.cocassistant.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity AccountProfile - Hồ sơ tài khoản để chuyển đổi qua Supercell ID
 */
@Entity(tableName = "account_profiles")
data class AccountProfile(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var accountName: String = "",
    var supercellId: String = "",
    var loginUrl: String = "",
    var isLogged: Boolean = false,
    var currentScene: String = "",
    var townHallLevel: Int = 0,
    var accessToken: String = "",
    var refreshToken: String = "",
    var creationDate: Date? = null,
    var lastUpdateDate: Date? = null,
    var status: String = "ACTIVE",
    var isDefaultAccount: Boolean = false
) {
    companion object {
        const val MAX_ACCOUNTS_SUPPORTED = 10
    }
}
