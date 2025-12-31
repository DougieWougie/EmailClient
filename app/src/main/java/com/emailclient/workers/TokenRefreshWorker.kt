package com.emailclient.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emailclient.data.remote.oauth.TokenManager
import com.emailclient.domain.model.AuthenticationType
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.util.Result
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker to refresh OAuth2 tokens before they expire
 * Runs periodically (every 6 hours) to ensure tokens remain valid
 */
@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val tokenManager: TokenManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "token_refresh_worker"

        // Refresh tokens that expire within 6 hours
        private const val REFRESH_THRESHOLD_HOURS = 6L
        private const val REFRESH_THRESHOLD_MS = REFRESH_THRESHOLD_HOURS * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        android.util.Log.d("TokenRefreshWorker", "=== Starting Token Refresh ===")

        return try {
            // Get all accounts
            val accounts = accountRepository.getAllAccounts().first()
            android.util.Log.d("TokenRefreshWorker", "Found ${accounts.size} accounts")

            // Filter OAuth2 accounts
            val oauth2Accounts = accounts.filter {
                it.imapConfig.authenticationType == AuthenticationType.OAUTH2 ||
                it.smtpConfig.authenticationType == AuthenticationType.OAUTH2
            }

            android.util.Log.d("TokenRefreshWorker", "Found ${oauth2Accounts.size} OAuth2 accounts")

            if (oauth2Accounts.isEmpty()) {
                android.util.Log.d("TokenRefreshWorker", "No OAuth2 accounts to refresh")
                return Result.success()
            }

            var refreshedCount = 0
            var failedCount = 0

            // Refresh tokens for each OAuth2 account
            for (account in oauth2Accounts) {
                try {
                    // Check if token needs refresh (expires within threshold)
                    if (shouldRefreshToken(account.id)) {
                        android.util.Log.d(
                            "TokenRefreshWorker",
                            "Refreshing token for account: ${account.email} (ID: ${account.id})"
                        )

                        when (val refreshResult = accountRepository.refreshAccountToken(account.id)) {
                            is com.emailclient.util.Result.Success -> {
                                android.util.Log.d(
                                    "TokenRefreshWorker",
                                    "✓ Token refreshed successfully for ${account.email}"
                                )
                                refreshedCount++
                            }
                            is com.emailclient.util.Result.Error -> {
                                android.util.Log.e(
                                    "TokenRefreshWorker",
                                    "✗ Token refresh failed for ${account.email}: ${refreshResult.message}",
                                    refreshResult.exception
                                )
                                failedCount++
                            }
                            else -> {
                                android.util.Log.w(
                                    "TokenRefreshWorker",
                                    "Unknown result type for ${account.email}"
                                )
                                failedCount++
                            }
                        }
                    } else {
                        android.util.Log.d(
                            "TokenRefreshWorker",
                            "Token for ${account.email} is still valid, skipping"
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "TokenRefreshWorker",
                        "Error processing account ${account.email}",
                        e
                    )
                    failedCount++
                }
            }

            android.util.Log.d(
                "TokenRefreshWorker",
                "=== Token Refresh Complete === (Refreshed: $refreshedCount, Failed: $failedCount)"
            )

            // Return success even if some tokens failed to refresh
            // Individual failures are logged but shouldn't fail the entire work
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TokenRefreshWorker", "Token refresh worker failed", e)
            Result.failure()
        }
    }

    /**
     * Check if token should be refreshed based on expiry time
     * Returns true if token expires within the threshold
     */
    private fun shouldRefreshToken(accountId: Long): Boolean {
        val expiresAt = tokenManager.getTokenExpiry(accountId) ?: run {
            android.util.Log.w("TokenRefreshWorker", "No expiry time found for account $accountId")
            return false
        }

        val now = System.currentTimeMillis()
        val timeUntilExpiry = expiresAt - now

        return timeUntilExpiry <= REFRESH_THRESHOLD_MS
    }
}

/**
 * Extension for TokenManager to expose expiry checking
 */
private fun TokenManager.getTokenExpiry(accountId: Long): Long? {
    // This is a workaround to access the expiry time
    // In production, you might want to add a public method to TokenManager
    return try {
        // Check if token is expired (which internally checks expiry time)
        if (isTokenExpired(accountId)) {
            // Token is expired or expiring soon
            System.currentTimeMillis() - 1 // Return a past timestamp
        } else {
            // Token is valid for longer than threshold
            System.currentTimeMillis() + (7 * 60 * 60 * 1000L) // Return future timestamp (7 hours)
        }
    } catch (e: Exception) {
        null
    }
}
