package com.emailclient.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.emailclient.domain.repository.AccountRepository
import com.emailclient.domain.repository.EmailRepository
import com.emailclient.domain.repository.FolderRepository
import com.emailclient.util.Result as ApiResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Background worker for syncing emails from all accounts
 */
@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val folderRepository: FolderRepository,
    private val emailRepository: EmailRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        return try {
            // Get all accounts that have sync enabled
            val accounts = accountRepository.getAllAccounts().first()
                .filter { it.syncEnabled }

            if (accounts.isEmpty()) {
                return androidx.work.ListenableWorker.Result.success()
            }

            var syncedCount = 0
            var errorCount = 0

            // Sync emails for each account
            accounts.forEach { account ->
                try {
                    // Get folders for this account
                    val folders = folderRepository.getFoldersByAccount(account.id).first()
                        .filter { it.syncEnabled }

                    // Sync emails for each enabled folder
                    folders.forEach { folder ->
                        val result = emailRepository.syncEmails(account.id, folder.id)
                        if (result is ApiResult.Success) {
                            syncedCount++
                        } else {
                            errorCount++
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCount++
                }
            }

            // Return success if at least one sync succeeded
            if (syncedCount > 0) {
                androidx.work.ListenableWorker.Result.success()
            } else if (errorCount > 0) {
                androidx.work.ListenableWorker.Result.retry()
            } else {
                androidx.work.ListenableWorker.Result.success()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            androidx.work.ListenableWorker.Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "email_sync_worker"
    }
}
