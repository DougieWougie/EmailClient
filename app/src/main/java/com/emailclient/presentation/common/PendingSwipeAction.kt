package com.emailclient.presentation.common

import com.emailclient.domain.model.SwipeAction

/**
 * Data class representing a pending swipe action that can be undone
 */
data class PendingSwipeAction(
    val emailId: String,
    val action: SwipeAction,
    val originalReadState: Boolean? = null,
    val originalFolderId: Long? = null
)
