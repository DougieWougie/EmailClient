package com.emailclient.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.SwipeAction

/**
 * Settings screen - Compose version
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToFolders: () -> Unit,
    onNavigateToAccountSetup: (Long?) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var accountToDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }

    // Handle UI state messages
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SettingsUiState.AccountDeleted -> {
                snackbarHostState.showSnackbar("Account deleted")
                viewModel.resetState()
            }
            is SettingsUiState.DefaultAccountSet -> {
                snackbarHostState.showSnackbar("Default account updated")
                viewModel.resetState()
            }
            is SettingsUiState.SyncStarted -> {
                snackbarHostState.showSnackbar("Sync started")
                viewModel.resetState()
            }
            is SettingsUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Accounts Section
            item {
                Text(
                    text = "Accounts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (accounts.isEmpty()) {
                item {
                    Text(
                        text = "No accounts configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(
                    items = accounts,
                    key = { it.id }
                ) { account ->
                    AccountListItem(
                        account = account,
                        onSetDefault = { viewModel.setDefaultAccount(account.id) },
                        onToggleSync = { enabled -> viewModel.toggleAccountSync(account.id, enabled) },
                        onToggleAutoDownloadImages = { enabled ->
                            viewModel.toggleAutoDownloadImages(account.id, enabled)
                        },
                        onEdit = { onNavigateToAccountSetup(account.id) },
                        onDelete = { accountToDelete = account.id to account.email }
                    )
                }
            }

            item {
                Button(
                    onClick = { onNavigateToAccountSetup(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Add Account")
                }
            }

            item {
                HorizontalDivider()
            }

            // Email Settings Section
            item {
                Text(
                    text = "Email Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SyncIntervalSetting(
                    viewModel = viewModel,
                    onShowMessage = { message ->
                        // Launch in effect to show snackbar
                    }
                )
            }

            item {
                SwipeActionSettings(
                    viewModel = viewModel,
                    onShowMessage = { message ->
                        // Launch in effect to show snackbar
                    }
                )
            }

            item {
                HorizontalDivider()
            }

            // App Settings Section
            item {
                Text(
                    text = "App Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                var animationsEnabled by remember { mutableStateOf(viewModel.areAnimationsEnabled()) }

                ListItem(
                    headlineContent = { Text("Activity animations") },
                    supportingContent = { Text("Enable transition animations") },
                    trailingContent = {
                        Switch(
                            checked = animationsEnabled,
                            onCheckedChange = { enabled ->
                                animationsEnabled = enabled
                                viewModel.setAnimationsEnabled(enabled)
                            }
                        )
                    }
                )
            }

            item {
                HorizontalDivider()
            }

            // Actions Section
            item {
                OutlinedButton(
                    onClick = onNavigateToFolders,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Folders")
                }
            }

            item {
                val isSyncing = uiState is SettingsUiState.Syncing

                OutlinedButton(
                    onClick = { viewModel.syncNow() },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text("Sync Now")
                }
            }
        }
    }

    // Delete confirmation dialog
    accountToDelete?.let { (accountId, email) ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete $email? This will remove all emails and folders for this account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(accountId)
                        accountToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AccountListItem(
    account: Account,
    onSetDefault: () -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onToggleAutoDownloadImages: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.email,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (account.isDefault) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onSetDefault) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Set as default",
                        tint = if (account.isDefault) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit account")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete account")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enable sync",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = account.syncEnabled,
                    onCheckedChange = onToggleSync
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Auto-download images",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = account.autoDownloadImages,
                    onCheckedChange = onToggleAutoDownloadImages
                )
            }
        }
    }
}

@Composable
private fun SyncIntervalSetting(
    viewModel: SettingsViewModel,
    onShowMessage: (String) -> Unit
) {
    val options = remember { viewModel.getSyncIntervalOptions() }
    val currentInterval = remember { viewModel.getSyncInterval() }
    val currentOption = remember { options.find { it.minutes == currentInterval } }

    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(currentOption ?: options.first()) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sync frequency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        selectedOption = option
                        viewModel.setSyncInterval(option.minutes)
                        expanded = false
                        onShowMessage("Sync frequency updated to ${option.label}")
                    }
                )
            }
        }
    }
}

@Composable
private fun SwipeActionSettings(
    viewModel: SettingsViewModel,
    onShowMessage: (String) -> Unit
) {
    val options = remember { viewModel.getSwipeActionOptions() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left Swipe
        SwipeActionDropdown(
            label = "Left swipe action",
            options = options,
            currentAction = viewModel.getSwipeLeftAction(),
            onActionSelected = { action ->
                viewModel.setSwipeLeftAction(action)
                onShowMessage("Left swipe action set to ${action.displayName}")
            }
        )

        // Right Swipe
        SwipeActionDropdown(
            label = "Right swipe action",
            options = options,
            currentAction = viewModel.getSwipeRightAction(),
            onActionSelected = { action ->
                viewModel.setSwipeRightAction(action)
                onShowMessage("Right swipe action set to ${action.displayName}")
            }
        )
    }
}

@Composable
private fun SwipeActionDropdown(
    label: String,
    options: List<SwipeAction>,
    currentAction: SwipeAction,
    onActionSelected: (SwipeAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf(currentAction) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedAction.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        selectedAction = option
                        onActionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
