package com.emailclient.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing an email account
 */
@Parcelize
data class Account(
    val id: Long = 0,
    val email: String,
    val displayName: String,
    val accountType: AccountType,
    val imapConfig: ServerConfig,
    val smtpConfig: ServerConfig,
    val isDefault: Boolean = false,
    val syncEnabled: Boolean = true,
    val lastSyncTime: Long = 0,
    val profileImageUri: String? = null,
    val autoDownloadImages: Boolean = false
) : Parcelable

@Parcelize
data class ServerConfig(
    val host: String,
    val port: Int,
    val username: String,
    val securityType: SecurityType,
    val authenticationType: AuthenticationType = AuthenticationType.PASSWORD
) : Parcelable

enum class AccountType {
    GMAIL,
    OUTLOOK,
    EXCHANGE,
    GENERIC
}

enum class SecurityType {
    NONE,
    SSL_TLS,
    STARTTLS
}

enum class AuthenticationType {
    PASSWORD,
    OAUTH2,
    CRAM_MD5
}
