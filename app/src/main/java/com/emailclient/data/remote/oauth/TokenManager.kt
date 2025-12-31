package com.emailclient.data.remote.oauth

import com.emailclient.data.local.CredentialManager
import com.emailclient.util.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages OAuth2 token lifecycle including expiry tracking and automatic refresh
 * Thread-safe implementation with mutex for concurrent access
 */
@Singleton
class TokenManager @Inject constructor(
    private val credentialManager: CredentialManager,
    private val oauth2Service: OAuth2Service
) {
    // Mutex for thread-safe token operations
    private val tokenMutex = Mutex()

    companion object {
        // Refresh token proactively 5 minutes before expiry
        private const val REFRESH_THRESHOLD_MS = 5 * 60 * 1000L
    }

    /**
     * Get a valid access token for the account
     * Automatically refreshes if expired or about to expire
     */
    suspend fun getValidAccessToken(accountId: Long): Result<String> = tokenMutex.withLock {
        return try {
            // Check if token exists
            val currentToken = credentialManager.getAccessToken(accountId)
            if (currentToken == null) {
                return Result.Error(
                    Exception("No access token found"),
                    "Access token not available for account"
                )
            }

            // Check if token needs refresh
            if (isTokenExpired(accountId)) {
                android.util.Log.d("TokenManager", "Token expired or expiring soon, refreshing...")
                return refreshTokenIfNeeded(accountId)
            }

            Result.Success(currentToken)
        } catch (e: Exception) {
            android.util.Log.e("TokenManager", "Error getting valid access token", e)
            Result.Error(e, "Failed to get access token: ${e.message}")
        }
    }

    /**
     * Save OAuth2 tokens with expiry timestamp
     */
    suspend fun saveTokens(
        accountId: Long,
        accessToken: String,
        refreshToken: String,
        expiresAt: Long
    ) = tokenMutex.withLock {
        try {
            credentialManager.saveAccessToken(accountId, accessToken)
            credentialManager.saveRefreshToken(accountId, refreshToken)
            credentialManager.saveTokenExpiry(accountId, expiresAt)
            android.util.Log.d("TokenManager", "Tokens saved for account $accountId, expires at $expiresAt")
        } catch (e: Exception) {
            android.util.Log.e("TokenManager", "Error saving tokens", e)
            throw e
        }
    }

    /**
     * Check if token is expired or will expire soon (within threshold)
     */
    fun isTokenExpired(accountId: Long): Boolean {
        val expiresAt = credentialManager.getTokenExpiry(accountId) ?: return true
        val now = System.currentTimeMillis()
        val isExpired = (expiresAt - now) <= REFRESH_THRESHOLD_MS

        if (isExpired) {
            android.util.Log.d(
                "TokenManager",
                "Token for account $accountId expires in ${(expiresAt - now) / 1000} seconds"
            )
        }

        return isExpired
    }

    /**
     * Refresh token if needed and return new access token
     */
    suspend fun refreshTokenIfNeeded(accountId: Long): Result<String> = tokenMutex.withLock {
        return try {
            android.util.Log.d("TokenManager", "Refreshing token for account $accountId")

            when (val result = oauth2Service.refreshAccessToken(accountId)) {
                is Result.Success -> {
                    val tokens = result.data

                    // Save new tokens
                    saveTokens(
                        accountId = accountId,
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresAt = tokens.expiresAt
                    )

                    android.util.Log.d("TokenManager", "Token refreshed successfully for account $accountId")
                    Result.Success(tokens.accessToken)
                }
                is Result.Error -> {
                    android.util.Log.e("TokenManager", "Token refresh failed: ${result.message}", result.exception)

                    // Check if it's a refresh token expiry error
                    if (result.exception.message?.contains("invalid_grant") == true ||
                        result.exception.message?.contains("expired") == true) {
                        // Refresh token expired - require re-authentication
                        revokeTokens(accountId)
                        Result.Error(
                            ReAuthenticationRequiredException("Refresh token expired. Please sign in again."),
                            "Re-authentication required"
                        )
                    } else {
                        result
                    }
                }
                else -> Result.Error(Exception("Unknown error during token refresh"))
            }
        } catch (e: Exception) {
            android.util.Log.e("TokenManager", "Error refreshing token", e)
            Result.Error(e, "Failed to refresh token: ${e.message}")
        }
    }

    /**
     * Revoke (delete) all tokens for an account
     * Called on account deletion or when refresh token expires
     */
    suspend fun revokeTokens(accountId: Long) = tokenMutex.withLock {
        try {
            credentialManager.deleteAccessToken(accountId)
            credentialManager.deleteRefreshToken(accountId)
            credentialManager.deleteTokenExpiry(accountId)
            android.util.Log.d("TokenManager", "Tokens revoked for account $accountId")
        } catch (e: Exception) {
            android.util.Log.e("TokenManager", "Error revoking tokens", e)
            throw e
        }
    }

    /**
     * Check if an account has OAuth2 tokens
     */
    fun hasTokens(accountId: Long): Boolean {
        return credentialManager.getAccessToken(accountId) != null &&
                credentialManager.getRefreshToken(accountId) != null
    }
}

/**
 * Exception thrown when re-authentication is required
 * (e.g., refresh token expired after 90 days)
 */
class ReAuthenticationRequiredException(message: String) : Exception(message)
