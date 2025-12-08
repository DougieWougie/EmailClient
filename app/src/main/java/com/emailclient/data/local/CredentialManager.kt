package com.emailclient.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages encrypted storage of email account credentials
 */
@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_email_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Store account password securely
     */
    fun savePassword(accountId: Long, password: String) {
        android.util.Log.d("CredentialManager", "Saving password for account $accountId (${password.length} chars)")
        encryptedPrefs.edit()
            .putString(getPasswordKey(accountId), password)
            .apply()
        android.util.Log.d("CredentialManager", "Password saved successfully for account $accountId")
    }

    /**
     * Retrieve account password
     */
    fun getPassword(accountId: Long): String? {
        val password = encryptedPrefs.getString(getPasswordKey(accountId), null)
        android.util.Log.d("CredentialManager", "Password retrieval for account $accountId: ${if (password != null) "Found (${password.length} chars)" else "NOT FOUND"}")
        return password
    }

    /**
     * Delete account password
     */
    fun deletePassword(accountId: Long) {
        encryptedPrefs.edit()
            .remove(getPasswordKey(accountId))
            .apply()
    }

    /**
     * Store OAuth2 access token
     */
    fun saveAccessToken(accountId: Long, token: String) {
        encryptedPrefs.edit()
            .putString(getAccessTokenKey(accountId), token)
            .apply()
    }

    /**
     * Retrieve OAuth2 access token
     */
    fun getAccessToken(accountId: Long): String? {
        return encryptedPrefs.getString(getAccessTokenKey(accountId), null)
    }

    /**
     * Store OAuth2 refresh token
     */
    fun saveRefreshToken(accountId: Long, token: String) {
        encryptedPrefs.edit()
            .putString(getRefreshTokenKey(accountId), token)
            .apply()
    }

    /**
     * Retrieve OAuth2 refresh token
     */
    fun getRefreshToken(accountId: Long): String? {
        return encryptedPrefs.getString(getRefreshTokenKey(accountId), null)
    }

    /**
     * Delete all credentials for an account
     */
    fun deleteAllCredentials(accountId: Long) {
        encryptedPrefs.edit()
            .remove(getPasswordKey(accountId))
            .remove(getAccessTokenKey(accountId))
            .remove(getRefreshTokenKey(accountId))
            .apply()
    }

    /**
     * Clear all stored credentials
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    private fun getPasswordKey(accountId: Long) = "password_$accountId"
    private fun getAccessTokenKey(accountId: Long) = "access_token_$accountId"
    private fun getRefreshTokenKey(accountId: Long) = "refresh_token_$accountId"
}
