package com.emailclient.presentation.oauth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

/**
 * OAuth2 login screen that handles Microsoft authentication via browser
 * Uses AppAuth library for OAuth2 authorization code flow with PKCE
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuth2LoginScreen(
    email: String,
    displayName: String,
    onAuthSuccess: (accessToken: String, refreshToken: String, expiresAt: Long) -> Unit,
    onAuthCancelled: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: OAuth2ViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // AppAuth activity launcher for browser-based OAuth2 flow
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val authResponse = AuthorizationResponse.fromIntent(data)
                val authException = AuthorizationException.fromIntent(data)

                when {
                    authResponse != null -> {
                        // Authorization successful - exchange code for tokens
                        viewModel.handleAuthorizationResponse(authResponse)
                    }
                    authException != null -> {
                        // Authorization failed or cancelled
                        viewModel.handleAuthorizationError(authException)
                    }
                    else -> {
                        viewModel.handleAuthorizationError(
                            AuthorizationException.fromTemplate(
                                AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW,
                                null
                            )
                        )
                    }
                }
            }
        } else {
            // User cancelled the browser flow
            viewModel.handleAuthorizationCancelled()
        }
    }

    // Launch OAuth2 flow when screen is first displayed
    LaunchedEffect(Unit) {
        val authIntent = viewModel.buildAuthorizationIntent()
        authLauncher.launch(authIntent)
    }

    // Handle state changes
    LaunchedEffect(state) {
        when (val currentState = state) {
            is OAuth2State.Success -> {
                onAuthSuccess(
                    currentState.accessToken,
                    currentState.refreshToken,
                    currentState.expiresAt
                )
            }
            is OAuth2State.Cancelled -> {
                onAuthCancelled()
            }
            else -> { /* Continue showing UI */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Microsoft Sign In") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is OAuth2State.Idle -> {
                    // Initial state - show loading
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Preparing Microsoft sign in...",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is OAuth2State.Processing -> {
                    // Processing authorization or token exchange
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Signing in as $email",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is OAuth2State.Error -> {
                    // Error occurred during OAuth2 flow
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Sign In Failed",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                // Retry OAuth2 flow
                                viewModel.resetState()
                                val authIntent = viewModel.buildAuthorizationIntent()
                                authLauncher.launch(authIntent)
                            }
                        ) {
                            Text("Try Again")
                        }
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }

                is OAuth2State.Success -> {
                    // Success - tokens received
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Sign In Successful",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Configuring your account...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is OAuth2State.Cancelled -> {
                    // User cancelled OAuth2 flow
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = "Cancelled",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Sign In Cancelled",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "You can use password authentication instead.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
    }
}
