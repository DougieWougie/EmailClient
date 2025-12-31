package com.emailclient.presentation.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emailclient.presentation.components.SwipeableEmailListItem

/**
 * Folder view screen - Compose version
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderViewScreen(
    folderId: Long,
    folderName: String,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCompose: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FolderViewViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val emails by viewModel.emails.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedEmailIds by viewModel.selectedEmailIds.collectAsStateWithLifecycle()
    val swipeActionResult by viewModel.swipeActionResult.collectAsStateWithLifecycle()

    val swipeLeftAction = viewModel.getSwipeLeftAction()
    val swipeRightAction = viewModel.getSwipeRightAction()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle swipe action result with snackbar
    LaunchedEffect(swipeActionResult) {
        swipeActionResult?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo"
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.undoSwipeAction()
                SnackbarResult.Dismissed -> viewModel.finalizeSwipeAction()
            }
            viewModel.clearSwipeActionResult()
        }
    }

    // Folder is automatically loaded in ViewModel init

    // Handle back press in selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedEmailIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, "Exit selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAllEmails() }) {
                            Icon(Icons.Filled.CheckCircle, "Select all")
                        }
                        IconButton(onClick = { viewModel.bulkMarkAsRead(true) }) {
                            Icon(Icons.Filled.Check, "Mark read")
                        }
                        IconButton(onClick = { viewModel.bulkArchive() }) {
                            Icon(Icons.Filled.Delete, "Archive")
                        }
                        IconButton(onClick = { viewModel.bulkDelete() }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            } else {
                TopAppBar(
                    title = { Text(folderName) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = onNavigateToCompose) {
                    Icon(Icons.Default.Edit, "Compose")
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                emails.isEmpty() -> {
                    Text(
                        text = "No emails in this folder",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = emails,
                            key = { email -> email.id }
                        ) { email ->
                            SwipeableEmailListItem(
                                email = email,
                                isSelected = email.id in selectedEmailIds,
                                isSelectionMode = isSelectionMode,
                                swipeLeftAction = swipeLeftAction,
                                swipeRightAction = swipeRightAction,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleEmailSelection(email.id)
                                    } else {
                                        onNavigateToDetail(email.id)
                                    }
                                },
                                onLongClick = {
                                    viewModel.enterSelectionMode(email.id)
                                },
                                onSwipe = { action ->
                                    viewModel.performSwipeAction(email.id, action)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
