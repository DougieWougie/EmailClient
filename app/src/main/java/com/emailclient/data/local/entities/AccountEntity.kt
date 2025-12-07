package com.emailclient.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.AccountType
import com.emailclient.domain.model.AuthenticationType
import com.emailclient.domain.model.SecurityType
import com.emailclient.domain.model.ServerConfig

/**
 * Room entity for storing email accounts
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["email"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val displayName: String,
    val accountType: AccountType,
    @Embedded(prefix = "imap_")
    val imapConfig: ServerConfigEntity,
    @Embedded(prefix = "smtp_")
    val smtpConfig: ServerConfigEntity,
    val isDefault: Boolean = false,
    val syncEnabled: Boolean = true,
    val lastSyncTime: Long = 0
)

data class ServerConfigEntity(
    val host: String,
    val port: Int,
    val username: String,
    val securityType: SecurityType,
    val authenticationType: AuthenticationType = AuthenticationType.PASSWORD
)

/**
 * Extension function to convert AccountEntity to domain Account model
 */
fun AccountEntity.toDomain(): Account {
    return Account(
        id = id,
        email = email,
        displayName = displayName,
        accountType = accountType,
        imapConfig = ServerConfig(
            host = imapConfig.host,
            port = imapConfig.port,
            username = imapConfig.username,
            securityType = imapConfig.securityType,
            authenticationType = imapConfig.authenticationType
        ),
        smtpConfig = ServerConfig(
            host = smtpConfig.host,
            port = smtpConfig.port,
            username = smtpConfig.username,
            securityType = smtpConfig.securityType,
            authenticationType = smtpConfig.authenticationType
        ),
        isDefault = isDefault,
        syncEnabled = syncEnabled,
        lastSyncTime = lastSyncTime
    )
}

/**
 * Extension function to convert domain Account model to AccountEntity
 */
fun Account.toEntity(): AccountEntity {
    return AccountEntity(
        id = id,
        email = email,
        displayName = displayName,
        accountType = accountType,
        imapConfig = ServerConfigEntity(
            host = imapConfig.host,
            port = imapConfig.port,
            username = imapConfig.username,
            securityType = imapConfig.securityType,
            authenticationType = imapConfig.authenticationType
        ),
        smtpConfig = ServerConfigEntity(
            host = smtpConfig.host,
            port = smtpConfig.port,
            username = smtpConfig.username,
            securityType = smtpConfig.securityType,
            authenticationType = smtpConfig.authenticationType
        ),
        isDefault = isDefault,
        syncEnabled = syncEnabled,
        lastSyncTime = lastSyncTime
    )
}
