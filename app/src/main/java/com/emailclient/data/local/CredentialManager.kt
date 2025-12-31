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
        encryptedPrefs.edit()
            .putString(getPasswordKey(accountId), password)
            .apply()
    }

    /**
     * Retrieve account password
     */
    fun getPassword(accountId: Long): String? {
        return encryptedPrefs.getString(getPasswordKey(accountId), null)
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
     * Delete OAuth2 access token
     */
    fun deleteAccessToken(accountId: Long) {
        encryptedPrefs.edit()
            .remove(getAccessTokenKey(accountId))
            .apply()
    }

    /**
     * Delete OAuth2 refresh token
     */
    fun deleteRefreshToken(accountId: Long) {
        encryptedPrefs.edit()
            .remove(getRefreshTokenKey(accountId))
            .apply()
    }

    /**
     * Store OAuth2 token expiry timestamp
     */
    fun saveTokenExpiry(accountId: Long, expiresAtMillis: Long) {
        encryptedPrefs.edit()
            .putLong(getTokenExpiryKey(accountId), expiresAtMillis)
            .apply()
    }

    /**
     * Retrieve OAuth2 token expiry timestamp
     */
    fun getTokenExpiry(accountId: Long): Long? {
        val expiry = encryptedPrefs.getLong(getTokenExpiryKey(accountId), -1L)
        return if (expiry == -1L) null else expiry
    }

    /**
     * Delete OAuth2 token expiry
     */
    fun deleteTokenExpiry(accountId: Long) {
        encryptedPrefs.edit()
            .remove(getTokenExpiryKey(accountId))
            .apply()
    }

    /**
     * Store OAuth2 state (code verifier for PKCE)
     */
    fun saveOAuthState(state: String) {
        encryptedPrefs.edit()
            .putString(OAUTH_STATE_KEY, state)
            .apply()
    }

    /**
     * Retrieve OAuth2 state (code verifier for PKCE)
     */
    fun getOAuthState(): String? {
        return encryptedPrefs.getString(OAUTH_STATE_KEY, null)
    }

    /**
     * Clear OAuth2 state after token exchange
     */
    fun clearOAuthState() {
        encryptedPrefs.edit()
            .remove(OAUTH_STATE_KEY)
            .apply()
    }

    /**
     * Delete all credentials for an account
     */
    fun deleteAllCredentials(accountId: Long) {
        encryptedPrefs.edit()
            .remove(getPasswordKey(accountId))
            .remove(getAccessTokenKey(accountId))
            .remove(getRefreshTokenKey(accountId))
            .remove(getTokenExpiryKey(accountId))
            .apply()
    }

    /**
     * Clear all stored credentials
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        private const val OAUTH_STATE_KEY = "oauth_state"
    }

    private fun getPasswordKey(accountId: Long) = "password_$accountId"
    private fun getAccessTokenKey(accountId: Long) = "access_token_$accountId"
    private fun getRefreshTokenKey(accountId: Long) = "refresh_token_$accountId"
    private fun getTokenExpiryKey(accountId: Long) = "token_expiry_$accountId"
}
