package com.emailclient.presentation.oauth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emailclient.data.remote.oauth.OAuth2Service
import com.emailclient.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import javax.inject.Inject

/**
 * ViewModel for OAuth2 authentication flow
 * Manages state and coordinates AppAuth authorization
 */
@HiltViewModel
class OAuth2ViewModel @Inject constructor(
    private val oauth2Service: OAuth2Service
) : ViewModel() {

    private val _state = MutableStateFlow<OAuth2State>(OAuth2State.Idle)
    val state: StateFlow<OAuth2State> = _state.asStateFlow()

    /**
     * Build AppAuth authorization intent for Microsoft OAuth2
     * Note: The actual intent creation happens in OAuth2Service which has Context
     */
    fun buildAuthorizationIntent(): Intent {
        android.util.Log.d("OAuth2ViewModel", "Building Microsoft OAuth2 authorization request")
        return oauth2Service.getAuthorizationIntent()
    }

    /**
     * Handle successful authorization response
     * Exchanges authorization code for access and refresh tokens
     */
    fun handleAuthorizationResponse(authResponse: AuthorizationResponse) {
        android.util.Log.d("OAuth2ViewModel", "Authorization successful, exchanging code for tokens")

        _state.value = OAuth2State.Processing("Completing sign in...")

        viewModelScope.launch {
            when (val result = oauth2Service.performTokenRequest(authResponse)) {
                is Result.Success -> {
                    val tokens = result.data
                    android.util.Log.d(
                        "OAuth2ViewModel",
                        "Token exchange successful, expires at: ${tokens.expiresAt}"
                    )
                    _state.value = OAuth2State.Success(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresAt = tokens.expiresAt
                    )
                }
                is Result.Error -> {
                    android.util.Log.e(
                        "OAuth2ViewModel",
                        "Token exchange failed: ${result.message}",
                        result.exception
                    )
                    _state.value = OAuth2State.Error(
                        result.message ?: "Failed to complete sign in. Please try again."
                    )
                }
                else -> {
                    _state.value = OAuth2State.Error("Unknown error occurred during token exchange")
                }
            }
        }
    }

    /**
     * Handle authorization error
     */
    fun handleAuthorizationError(exception: AuthorizationException) {
        android.util.Log.e(
            "OAuth2ViewModel",
            "Authorization failed: ${exception.type} - ${exception.error}",
            exception
        )

        val errorMessage = when (exception.type) {
            AuthorizationException.TYPE_GENERAL_ERROR -> {
                when (exception.code) {
                    AuthorizationException.GeneralErrors.NETWORK_ERROR.code ->
                        "Network error. Please check your internet connection."
                    AuthorizationException.GeneralErrors.SERVER_ERROR.code ->
                        "Microsoft server error. Please try again later."
                    AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code,
                    AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW.code ->
                        "Sign in was cancelled."
                    else -> "Sign in failed: ${exception.error ?: "Unknown error"}"
                }
            }
            AuthorizationException.TYPE_OAUTH_AUTHORIZATION_ERROR ->
                "Microsoft authorization failed: ${exception.error ?: "Access denied"}"
            AuthorizationException.TYPE_OAUTH_TOKEN_ERROR ->
                "Failed to obtain access token. Please try again."
            AuthorizationException.TYPE_OAUTH_REGISTRATION_ERROR ->
                "OAuth2 registration error. Please contact support."
            else -> "Sign in failed: ${exception.errorDescription ?: exception.error ?: "Unknown error"}"
        }

        // Check if it was a cancellation
        if (exception.type == AuthorizationException.TYPE_GENERAL_ERROR &&
            (exception.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code ||
             exception.code == AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW.code)) {
            _state.value = OAuth2State.Cancelled
        } else {
            _state.value = OAuth2State.Error(errorMessage)
        }
    }

    /**
     * Handle user cancellation of OAuth2 flow
     */
    fun handleAuthorizationCancelled() {
        android.util.Log.d("OAuth2ViewModel", "User cancelled OAuth2 authorization")
        _state.value = OAuth2State.Cancelled
    }

    /**
     * Reset state to idle (for retry)
     */
    fun resetState() {
        _state.value = OAuth2State.Idle
    }
}

/**
 * Sealed class representing OAuth2 flow states
 */
sealed class OAuth2State {
    /**
     * Initial state - not started
     */
    data object Idle : OAuth2State()

    /**
     * Processing authorization or token exchange
     */
    data class Processing(val message: String) : OAuth2State()

    /**
     * OAuth2 flow successful - tokens obtained
     */
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    ) : OAuth2State()

    /**
     * OAuth2 flow failed with error
     */
    data class Error(val message: String) : OAuth2State()

    /**
     * User cancelled the OAuth2 flow
     */
    data object Cancelled : OAuth2State()
}
