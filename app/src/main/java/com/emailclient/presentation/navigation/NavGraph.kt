package com.emailclient.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.emailclient.presentation.compose.ComposeEmailScreen
import com.emailclient.presentation.detail.EmailDetailScreen
import com.emailclient.presentation.folder.FolderViewScreen
import com.emailclient.presentation.folders.FolderManagementScreen
import com.emailclient.presentation.inbox.InboxScreen
import com.emailclient.presentation.oauth.OAuth2LoginScreen
import com.emailclient.presentation.settings.SettingsScreen
import com.emailclient.presentation.setup.AccountSetupViewModel
import com.emailclient.presentation.setup.ManualConfigScreen
import com.emailclient.presentation.setup.WelcomeScreen

@Composable
fun EmailClientNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Inbox.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Inbox.route) {
            InboxScreen(
                onNavigateToDetail = { emailId ->
                    navController.navigate(Screen.EmailDetail.createRoute(emailId))
                },
                onNavigateToCompose = {
                    navController.navigate(Screen.Compose.route)
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable(
            route = Screen.EmailDetail.route,
            arguments = listOf(navArgument("emailId") { type = NavType.StringType })
        ) { backStackEntry ->
            val emailId = backStackEntry.arguments?.getString("emailId") ?: return@composable
            EmailDetailScreen(
                emailId = emailId,
                onNavigateBack = { navController.navigateUp() },
                onNavigateToCompose = { replyToEmailId, isReplyAll, isForward ->
                    navController.navigate(
                        Screen.Compose.createRoute(replyToEmailId, isReplyAll, isForward)
                    )
                }
            )
        }

        composable(
            route = Screen.Compose.route,
            arguments = listOf(
                navArgument("replyToEmailId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("isReplyAll") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("isForward") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val replyToEmailId = backStackEntry.arguments?.getString("replyToEmailId")
            val isReplyAll = backStackEntry.arguments?.getBoolean("isReplyAll") ?: false
            val isForward = backStackEntry.arguments?.getBoolean("isForward") ?: false

            ComposeEmailScreen(
                replyToEmailId = replyToEmailId,
                isReplyAll = isReplyAll,
                isForward = isForward,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToFolders = {
                    navController.navigate(Screen.FolderManagement.route)
                },
                onNavigateToAccountSetup = { accountId ->
                    navController.navigate(Screen.ManualConfig.createRoute(accountId))
                }
            )
        }

        composable(
            route = Screen.FolderView.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType },
                navArgument("folderName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: return@composable
            val folderName = backStackEntry.arguments?.getString("folderName") ?: return@composable

            FolderViewScreen(
                folderId = folderId,
                folderName = folderName,
                onNavigateToDetail = { emailId ->
                    navController.navigate(Screen.EmailDetail.createRoute(emailId))
                },
                onNavigateToCompose = {
                    navController.navigate(Screen.Compose.route)
                },
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(Screen.FolderManagement.route) {
            FolderManagementScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onSetupAccount = {
                    navController.navigate(Screen.ManualConfig.route)
                }
            )
        }

        composable(
            route = Screen.ManualConfig.route,
            arguments = listOf(
                navArgument("accountId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId")?.takeIf { it != -1L }

            ManualConfigScreen(
                accountId = accountId,
                onNavigateBack = { navController.navigateUp() },
                onAccountSaved = { navController.navigateUp() },
                onNavigateToOAuth2 = { email, displayName ->
                    navController.navigate(Screen.OAuth2Login.createRoute(email, displayName))
                }
            )
        }

        composable(
            route = Screen.OAuth2Login.route,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: return@composable
            val displayName = backStackEntry.arguments?.getString("displayName") ?: return@composable

            // Get shared AccountSetupViewModel from parent nav graph
            val setupViewModel: AccountSetupViewModel = hiltViewModel()

            OAuth2LoginScreen(
                email = email,
                displayName = displayName,
                onAuthSuccess = { accessToken, refreshToken, expiresAt ->
                    // Add OAuth2 account via shared ViewModel
                    setupViewModel.addOAuth2Account(
                        email = email,
                        displayName = displayName,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt
                    )
                    // Navigate back to inbox on success
                    navController.navigate(Screen.Inbox.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onAuthCancelled = {
                    // Navigate back to manual config screen
                    navController.navigateUp()
                },
                onNavigateBack = {
                    navController.navigateUp()
                }
            )
        }
    }
}
