package com.emailclient.presentation.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Compose email screen - Compose version
 * (Note: renamed from ComposeFragment to avoid naming confusion with Jetpack Compose)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEmailScreen(
    replyToEmailId: String? = null,
    isReplyAll: Boolean = false,
    isForward: Boolean = false,
    onNavigateBack: () -> Unit,
    viewModel: ComposeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val composeData by viewModel.composeData.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()

    var to by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var bcc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    // File picker for attachments
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addAttachment(uri)
        }
    }

    // Initialize for reply/forward
    LaunchedEffect(replyToEmailId) {
        replyToEmailId?.let {
            viewModel.prepareReplyOrForward(it, isReplyAll, isForward)
        }
    }

    // Update local state when composeData changes
    LaunchedEffect(composeData) {
        composeData?.let {
            to = it.to
            cc = it.cc
            subject = it.subject
            body = it.body
        }
    }

    // Handle sending success
    LaunchedEffect(uiState) {
        when (uiState) {
            is ComposeState.Success -> {
                onNavigateBack()
            }
            is ComposeState.Error -> {
                snackbarHostState.showSnackbar((uiState as ComposeState.Error).message)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isReplyAll -> "Reply All"
                            isForward -> "Forward"
                            replyToEmailId != null -> "Reply"
                            else -> "New Email"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Filled.Add, "Attach file")
                    }
                    IconButton(
                        onClick = {
                            viewModel.sendEmail(
                                to = to,
                                cc = cc,
                                bcc = bcc,
                                subject = subject,
                                body = body
                            )
                        },
                        enabled = uiState !is ComposeState.Sending
                    ) {
                        Icon(Icons.Default.Send, "Send")
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
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To") },
                placeholder = { Text("recipient@example.com") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = cc,
                onValueChange = { cc = it },
                label = { Text("Cc (optional)") },
                placeholder = { Text("cc@example.com") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = bcc,
                onValueChange = { bcc = it },
                label = { Text("Bcc (optional)") },
                placeholder = { Text("bcc@example.com") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                placeholder = { Text("Email subject") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                placeholder = { Text("Compose your email...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 10
            )

            if (uiState is ComposeState.Sending) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sending...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
