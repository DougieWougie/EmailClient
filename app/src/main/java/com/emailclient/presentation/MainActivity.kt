package com.emailclient.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.emailclient.domain.model.FolderType
import com.emailclient.presentation.navigation.EmailClientNavHost
import com.emailclient.presentation.navigation.Screen
import com.emailclient.presentation.theme.EmailClientTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Main Activity - Compose version
 * Migrated from Fragment-based UI to Jetpack Compose
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EmailClientTheme {
                EmailClientApp(viewModel)
            }
        }
    }
}

@Composable
fun EmailClientApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val hasAccounts by viewModel.hasAccounts.collectAsStateWithLifecycle()

    // Show welcome screen if no accounts exist
    if (!hasAccounts) {
        EmailClientNavHost(
            navController = navController,
            onOpenDrawer = { /* No drawer when no accounts */ },
            startDestination = Screen.Welcome.route
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Account Switcher
                val currentAccount by viewModel.currentAccount.collectAsStateWithLifecycle()
                val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
                var accountMenuExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Email Client",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Current Account Dropdown
                    if (currentAccount != null && allAccounts.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { accountMenuExpanded = true }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentAccount?.email ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Switch account"
                            )
                        }

                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            allAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.email) },
                                    onClick = {
                                        viewModel.switchAccount(account.id)
                                        accountMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.AccountCircle,
                                            contentDescription = null,
                                            tint = if (account.id == currentAccount?.id)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                )
                            }
                        }
                    } else if (currentAccount != null) {
                        // Single account - just display it
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentAccount?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                // Folders from ViewModel
                folders.filter { it.type != FolderType.CUSTOM }.forEach { folder ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = if (folder.type == FolderType.TRASH)
                                    Icons.Filled.Delete
                                else
                                    Icons.Filled.Email,
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(
                                if (folder.unreadCount > 0)
                                    "${folder.displayName} (${folder.unreadCount})"
                                else
                                    folder.displayName
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                navController.navigate(
                                    Screen.FolderView.createRoute(folder.id, folder.displayName)
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                // Custom folders
                val customFolders = folders.filter { it.type == FolderType.CUSTOM }
                if (customFolders.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    customFolders.forEach { folder ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                            label = {
                                Text(
                                    if (folder.unreadCount > 0)
                                        "${folder.displayName} (${folder.unreadCount})"
                                    else
                                        folder.displayName
                                )
                            },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate(
                                        Screen.FolderView.createRoute(folder.id, folder.displayName)
                                    )
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Folder Management
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    label = { Text("Manage Folders") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Screen.FolderManagement.route)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Settings
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Screen.Settings.route)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        EmailClientNavHost(
            navController = navController,
            onOpenDrawer = {
                scope.launch {
                    drawerState.open()
                }
            }
        )
    }
}
