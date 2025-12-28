package com.emailclient.presentation.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.emailclient.domain.model.SecurityType

/**
 * Manual email account configuration screen - Compose version
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualConfigScreen(
    accountId: Long? = null,
    onNavigateBack: () -> Unit,
    onAccountSaved: () -> Unit,
    viewModel: AccountSetupViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val discoveredConfig by viewModel.discoveredConfig.collectAsStateWithLifecycle()
    val editingAccount by viewModel.editingAccount.collectAsStateWithLifecycle()
    val editingAccountPassword by viewModel.editingAccountPassword.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var imapServer by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var smtpServer by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var profileImageUri by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Try to persist URI permission for later access
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // URI doesn't support persistent permissions, but we can still use it
            }
            profileImageUri = it.toString()
            viewModel.setProfileImage(it.toString())
        }
    }

    // Load account for editing if accountId is provided
    LaunchedEffect(accountId) {
        accountId?.let {
            viewModel.loadAccountForEdit(it)
        }
    }

    // Update fields when editing account is loaded
    LaunchedEffect(editingAccount, editingAccountPassword) {
        editingAccount?.let { account ->
            email = account.email
            displayName = account.displayName
            imapServer = account.imapConfig.host
            imapPort = account.imapConfig.port.toString()
            smtpServer = account.smtpConfig.host
            smtpPort = account.smtpConfig.port.toString()
            password = editingAccountPassword ?: ""
            profileImageUri = account.profileImageUri
            viewModel.setProfileImage(account.profileImageUri)
        }
    }

    // Update fields when autoconfiguration succeeds
    LaunchedEffect(discoveredConfig) {
        discoveredConfig?.let { config ->
            imapServer = config.imapConfig.host
            imapPort = config.imapConfig.port.toString()
            smtpServer = config.smtpConfig.host
            smtpPort = config.smtpConfig.port.toString()
        }
    }

    // Handle UI state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AccountSetupState.DiscoverySuccess -> {
                snackbarHostState.showSnackbar("Auto-configuration successful! Settings have been filled in.")
            }
            is AccountSetupState.DiscoveryFailed -> {
                snackbarHostState.showSnackbar("Auto-configuration failed. Please enter settings manually.")
            }
            is AccountSetupState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            is AccountSetupState.Success -> {
                snackbarHostState.showSnackbar("Account saved successfully!")
                onAccountSaved()
            }
            is AccountSetupState.TestSuccess -> {
                // Automatically add account after successful test
                viewModel.addAccount(state.account, state.password)
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (accountId != null) "Edit Account" else "Add Account") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Email Account",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Image Picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile image display
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileImageUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(profileImageUri),
                                contentDescription = "Profile picture",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = "Default profile picture",
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Change photo button
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (profileImageUri != null) "Change Photo" else "Add Photo")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                placeholder = { Text("Your Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-configuration button
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        if (email.isNotBlank()) {
                            viewModel.discoverSettings(email)
                        }
                    },
                    enabled = email.isNotBlank() && uiState !is AccountSetupState.Discovering,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState is AccountSetupState.Discovering) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(20.dp)
                                .width(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Auto-Configure")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Incoming Mail (IMAP)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = imapServer,
                onValueChange = { imapServer = it },
                label = { Text("IMAP Server") },
                placeholder = { Text("imap.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = imapPort,
                onValueChange = { imapPort = it },
                label = { Text("IMAP Port") },
                placeholder = { Text("993") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Outgoing Mail (SMTP)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = smtpServer,
                onValueChange = { smtpServer = it },
                label = { Text("SMTP Server") },
                placeholder = { Text("smtp.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = smtpPort,
                onValueChange = { smtpPort = it },
                label = { Text("SMTP Port") },
                placeholder = { Text("465") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    // Validate and save
                    if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
                        return@Button
                    }

                    val imapPortInt = imapPort.toIntOrNull() ?: 993
                    val smtpPortInt = smtpPort.toIntOrNull() ?: 465

                    // Test connection first, then add account
                    viewModel.testConnection(
                        email = email,
                        password = password,
                        displayName = displayName.ifBlank { email },
                        imapHost = imapServer,
                        imapPort = imapPortInt,
                        imapSecurity = SecurityType.SSL_TLS,
                        smtpHost = smtpServer,
                        smtpPort = smtpPortInt,
                        smtpSecurity = SecurityType.SSL_TLS
                    )
                },
                enabled = email.isNotBlank() && password.isNotBlank() &&
                          imapServer.isNotBlank() && smtpServer.isNotBlank() &&
                          uiState !is AccountSetupState.Testing &&
                          uiState !is AccountSetupState.Adding,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is AccountSetupState.Testing || uiState is AccountSetupState.Adding) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState is AccountSetupState.Testing) "Testing..."
                     else if (uiState is AccountSetupState.Adding) "Saving..."
                     else "Save Account")
            }
        }
    }
}
