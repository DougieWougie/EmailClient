package com.emailclient.presentation.navigation

sealed class Screen(val route: String) {
    object Inbox : Screen("inbox")

    object EmailDetail : Screen("email/{emailId}") {
        fun createRoute(emailId: String) = "email/$emailId"
    }

    object Compose : Screen("compose?replyToEmailId={replyToEmailId}&isReplyAll={isReplyAll}&isForward={isForward}") {
        fun createRoute(
            replyToEmailId: String? = null,
            isReplyAll: Boolean = false,
            isForward: Boolean = false
        ) = buildString {
            append("compose")
            if (replyToEmailId != null) {
                append("?replyToEmailId=$replyToEmailId")
                append("&isReplyAll=$isReplyAll")
                append("&isForward=$isForward")
            }
        }
    }

    object Settings : Screen("settings")

    object FolderView : Screen("folder/{folderId}/{folderName}") {
        fun createRoute(folderId: Long, folderName: String) = "folder/$folderId/$folderName"
    }

    object FolderManagement : Screen("folder_management")

    object Welcome : Screen("welcome")

    object ManualConfig : Screen("manual_config?accountId={accountId}") {
        fun createRoute(accountId: Long? = null) = if (accountId != null) {
            "manual_config?accountId=$accountId"
        } else {
            "manual_config"
        }
    }
}
