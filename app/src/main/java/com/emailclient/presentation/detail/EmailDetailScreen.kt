package com.emailclient.presentation.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ReplyAll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emailclient.presentation.components.HtmlEmailContent
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Email detail screen - Compose version
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    emailId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCompose: (String?, Boolean, Boolean) -> Unit,
    viewModel: EmailDetailViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val email by viewModel.email.collectAsStateWithLifecycle()
    val imagesLoaded by viewModel.imagesLoaded.collectAsStateWithLifecycle()
    val shouldShowLoadImagesButton by viewModel.shouldShowLoadImagesButton.collectAsStateWithLifecycle()

    LaunchedEffect(emailId) {
        viewModel.loadEmail(emailId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToCompose(emailId, false, false) }) {
                        Icon(Icons.Filled.Reply, "Reply")
                    }
                    IconButton(onClick = { onNavigateToCompose(emailId, true, false) }) {
                        Icon(Icons.Filled.ReplyAll, "Reply all")
                    }
                    IconButton(onClick = { onNavigateToCompose(emailId, false, true) }) {
                        Icon(Icons.Filled.Forward, "Forward")
                    }
                    IconButton(onClick = { viewModel.archiveEmail(emailId) }) {
                        Icon(Icons.Filled.Archive, "Archive")
                    }
                    IconButton(onClick = { viewModel.deleteEmail(emailId) }) {
                        Icon(Icons.Filled.Delete, "Delete")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        email?.let { emailData ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Email header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = emailData.subject,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "From: ${emailData.from}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "To: ${emailData.to}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!emailData.cc.isNullOrEmpty()) {
                            Text(
                                text = "Cc: ${emailData.cc}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                .format(emailData.receivedDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    HorizontalDivider()
                }

                // Load images button
                if (shouldShowLoadImagesButton && !imagesLoaded) {
                    item {
                        Button(
                            onClick = { viewModel.loadImages() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("Load external images")
                        }
                    }
                }

                // Email body
                item {
                    HtmlEmailContent(
                        htmlContent = emailData.htmlBody ?: emailData.body,
                        allowExternalImages = imagesLoaded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
