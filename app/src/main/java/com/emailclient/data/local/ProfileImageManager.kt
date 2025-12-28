package com.emailclient.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure storage and access to profile images.
 * Handles URI permissions and copies images to internal storage when necessary.
 */
@Singleton
class ProfileImageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val profileImagesDir = File(context.filesDir, "profile_images").also {
        it.mkdirs()
    }

    sealed class Result {
        data class Success(val imageUri: String) : Result()
        data class Error(val exception: Exception, val message: String) : Result()
    }

    /**
     * Saves a profile image from the given source URI.
     * Attempts to take persistent URI permission first.
     * If that fails, copies the image to internal storage.
     */
    suspend fun saveProfileImage(sourceUri: Uri, accountId: Long): Result = withContext(Dispatchers.IO) {
        try {
            // Try to take persistent permission first
            var usedPersistentUri = false
            try {
                context.contentResolver.takePersistableUriPermission(
                    sourceUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                usedPersistentUri = true
            } catch (e: SecurityException) {
                // Permission not available, will copy file instead
                android.util.Log.d("ProfileImageManager", "Persistent permission not available, copying file")
            }

            if (usedPersistentUri) {
                // Verify we can actually read the URI
                try {
                    context.contentResolver.openInputStream(sourceUri)?.use { }
                    return@withContext Result.Success(sourceUri.toString())
                } catch (e: Exception) {
                    // Permission taken but can't read - fall through to copy
                    android.util.Log.w("ProfileImageManager", "Cannot read URI despite permission, copying", e)
                }
            }

            // Copy to internal storage
            val fileName = "profile_${accountId}_${System.currentTimeMillis()}.jpg"
            val outputFile = File(profileImagesDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Cannot open source URI")

            // Verify the file was written successfully
            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw Exception("Failed to save image file")
            }

            Result.Success("file://${outputFile.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("ProfileImageManager", "Failed to save profile image", e)
            Result.Error(e, "Failed to save profile image: ${e.message}")
        }
    }

    /**
     * Deletes a profile image and releases any associated permissions.
     */
    suspend fun deleteProfileImage(imageUri: String?) = withContext(Dispatchers.IO) {
        if (imageUri == null) return@withContext

        try {
            val uri = Uri.parse(imageUri)
            when (uri.scheme) {
                "file" -> {
                    // Delete from internal storage
                    val file = File(uri.path ?: return@withContext)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                "content" -> {
                    // Release persistent permission
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Permission may not exist, ignore
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileImageManager", "Error deleting profile image", e)
        }
    }

    /**
     * Cleans up old profile images for an account when updating.
     * Keeps only the most recent image.
     */
    suspend fun cleanupOldImages(accountId: Long, currentImageUri: String?) = withContext(Dispatchers.IO) {
        try {
            val prefix = "profile_${accountId}_"
            profileImagesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix)) {
                    val fileUri = "file://${file.absolutePath}"
                    if (fileUri != currentImageUri) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileImageManager", "Error cleaning up old images", e)
        }
    }
}
