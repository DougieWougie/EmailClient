package com.emailclient.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emailclient.domain.model.Email
import com.emailclient.domain.model.SwipeAction

/**
 * Swipeable email list item with Material3 SwipeToDismiss
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEmailListItem(
    email: Email,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    swipeLeftAction: SwipeAction,
    swipeRightAction: SwipeAction,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipe: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (swipeRightAction != SwipeAction.NONE) {
                        onSwipe(swipeRightAction)
                        true
                    } else {
                        false
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (swipeLeftAction != SwipeAction.NONE) {
                        onSwipe(swipeLeftAction)
                        true
                    } else {
                        false
                    }
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    // Disable swipe in selection mode
    val enableDismissFromStartToEnd = !isSelectionMode && swipeRightAction != SwipeAction.NONE
    val enableDismissFromEndToStart = !isSelectionMode && swipeLeftAction != SwipeAction.NONE

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = enableDismissFromStartToEnd,
        enableDismissFromEndToStart = enableDismissFromEndToStart,
        backgroundContent = {
            SwipeBackground(
                dismissState = dismissState,
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                isRead = email.isRead
            )
        },
        modifier = modifier
    ) {
        EmailListItem(
            email = email,
            isSelected = isSelected,
            isSelectionMode = isSelectionMode,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState: SwipeToDismissBoxState,
    swipeLeftAction: SwipeAction,
    swipeRightAction: SwipeAction,
    isRead: Boolean
) {
    val direction = dismissState.dismissDirection
    val color by animateColorAsState(
        targetValue = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> getActionColor(swipeRightAction)
            SwipeToDismissBoxValue.EndToStart -> getActionColor(swipeLeftAction)
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        label = "swipe_background_color"
    )

    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    val icon = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> getActionIcon(swipeRightAction, isRead)
        SwipeToDismissBoxValue.EndToStart -> getActionIcon(swipeLeftAction, isRead)
        SwipeToDismissBoxValue.Settled -> null
    }

    val scale by animateFloatAsState(
        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1f,
        label = "swipe_icon_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.scale(scale)
            )
        }
    }
}

@Composable
private fun getActionColor(action: SwipeAction): Color {
    return when (action) {
        SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.tertiary
        SwipeAction.DELETE -> MaterialTheme.colorScheme.error
        SwipeAction.MARK_READ -> MaterialTheme.colorScheme.primary
        SwipeAction.NONE -> Color.Transparent
    }
}

@Composable
private fun getActionIcon(action: SwipeAction, isRead: Boolean) = when (action) {
    SwipeAction.ARCHIVE -> Icons.Default.Archive
    SwipeAction.DELETE -> Icons.Default.Delete
    SwipeAction.MARK_READ -> if (isRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead
    SwipeAction.NONE -> null
}
