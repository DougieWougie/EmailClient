package com.emailclient.data.remote.oauth

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import com.emailclient.data.local.CredentialManager
import com.emailclient.util.Result
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * OAuth2 service for Microsoft authentication using AppAuth library
 * Handles authorization flow, token exchange, and token refresh
 */
@Singleton
class OAuth2Service @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialManager: CredentialManager
) {
    companion object {
        // Microsoft OAuth2 endpoints
        const val AUTHORIZATION_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        const val TOKEN_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/token"

        // Redirect URI configured in build.gradle
        const val REDIRECT_URI = "com.emailclient:/oauth2redirect"

        // TODO: Replace with actual Microsoft Client ID after app registration
        // Register app at: https://portal.azure.com
        const val CLIENT_ID = "your-microsoft-client-id-here"

        // Required scopes for IMAP/SMTP access
        private val SCOPES = listOf(
            "offline_access",  // Required for refresh tokens
            "https://outlook.office.com/IMAP.AccessAsUser.All",
            "https://outlook.office.com/SMTP.Send"
        )
    }

    private val authService = AuthorizationService(context)

    /**
     * Build Microsoft OAuth2 authorization request with PKCE
     */
    fun buildMicrosoftAuthRequest(): AuthorizationRequest {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(AUTHORIZATION_ENDPOINT),
            Uri.parse(TOKEN_ENDPOINT)
        )

        // Generate PKCE code verifier and challenge
        val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
        val codeChallenge = CodeVerifierUtil.deriveCodeVerifierChallenge(codeVerifier)

        // Store code verifier for token exchange (retrieved later)
        credentialManager.saveOAuthState(codeVerifier)

        return AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        )
            .setScopes(SCOPES)
            .setCodeVerifier(codeVerifier, codeChallenge, "S256")
            .build()
    }

    /**
     * Get authorization intent for launching browser-based OAuth2 flow
     */
    fun getAuthorizationIntent(): android.content.Intent {
        val authRequest = buildMicrosoftAuthRequest()
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Exchange authorization code for access and refresh tokens
     */
    suspend fun performTokenRequest(authResponse: AuthorizationResponse): Result<OAuth2Tokens> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // Retrieve code verifier stored during authorization (for PKCE verification)
                credentialManager.getOAuthState()
                    ?: return@withContext Result.Error(
                        Exception("Code verifier not found"),
                        "OAuth2 state verification failed"
                    )

                val tokenRequest = authResponse.createTokenExchangeRequest()

                val tokens = suspendCoroutine<OAuth2Tokens> { continuation ->
                    authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                        when {
                            tokenResponse != null -> {
                                val tokens = OAuth2Tokens(
                                    accessToken = tokenResponse.accessToken ?: "",
                                    refreshToken = tokenResponse.refreshToken ?: "",
                                    expiresAt = tokenResponse.accessTokenExpirationTime ?: 0L,
                                    tokenType = tokenResponse.tokenType ?: "Bearer"
                                )
                                continuation.resume(tokens)
                            }
                            exception != null -> {
                                throw exception
                            }
                            else -> {
                                throw Exception("Unknown token exchange error")
                            }
                        }
                    }
                }

                // Clear OAuth state after successful exchange
                credentialManager.clearOAuthState()

                Result.Success(tokens)
            } catch (e: Exception) {
                android.util.Log.e("OAuth2Service", "Token exchange failed", e)
                Result.Error(e, "Failed to exchange authorization code: ${e.message}")
            }
        }

    /**
     * Refresh access token using refresh token
     */
    suspend fun refreshAccessToken(accountId: Long): Result<OAuth2Tokens> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val refreshToken = credentialManager.getRefreshToken(accountId)
                    ?: return@withContext Result.Error(
                        Exception("Refresh token not found"),
                        "No refresh token available for account"
                    )

                val serviceConfig = AuthorizationServiceConfiguration(
                    Uri.parse(AUTHORIZATION_ENDPOINT),
                    Uri.parse(TOKEN_ENDPOINT)
                )

                val tokenRequest = TokenRequest.Builder(serviceConfig, CLIENT_ID)
                    .setGrantType("refresh_token")
                    .setRefreshToken(refreshToken)
                    .setScopes(SCOPES)
                    .build()

                val tokens = suspendCoroutine<OAuth2Tokens> { continuation ->
                    authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                        when {
                            tokenResponse != null -> {
                                val tokens = OAuth2Tokens(
                                    accessToken = tokenResponse.accessToken ?: "",
                                    refreshToken = tokenResponse.refreshToken ?: refreshToken, // Use old refresh token if not returned
                                    expiresAt = tokenResponse.accessTokenExpirationTime ?: 0L,
                                    tokenType = tokenResponse.tokenType ?: "Bearer"
                                )
                                continuation.resume(tokens)
                            }
                            exception != null -> {
                                throw exception
                            }
                            else -> {
                                throw Exception("Unknown token refresh error")
                            }
                        }
                    }
                }

                Result.Success(tokens)
            } catch (e: Exception) {
                android.util.Log.e("OAuth2Service", "Token refresh failed", e)
                Result.Error(e, "Failed to refresh access token: ${e.message}")
            }
        }

    /**
     * Dispose of AuthorizationService resources
     */
    fun dispose() {
        authService.dispose()
    }
}

/**
 * Data class representing OAuth2 tokens
 */
data class OAuth2Tokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,  // Unix timestamp in milliseconds
    val tokenType: String = "Bearer"
)
