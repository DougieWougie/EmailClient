package com.emailclient.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.emailclient.util.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val attachmentsDir: File
        get() = File(context.cacheDir, "attachments").also { it.mkdirs() }

    companion object {
        private const val MAX_FILE_SIZE = 25 * 1024 * 1024L // 25 MB
        private const val MAX_CACHE_SIZE = 200 * 1024 * 1024L // 200 MB
        private const val MAX_FILENAME_LENGTH = 255
        private const val MAX_AGE_DAYS = 7
    }

    /**
     * Save attachment to cache directory
     */
    suspend fun saveAttachment(
        emailId: String,
        attachmentId: String,
        fileName: String,
        inputStream: InputStream
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Validate file size before saving
            val availableBytes = inputStream.available().toLong()
            if (availableBytes > MAX_FILE_SIZE) {
                return@withContext Result.Error(
                    Exception("File size exceeds maximum of 25 MB")
                )
            }

            // Check cache size
            if (getCacheSizeBytes() + availableBytes > MAX_CACHE_SIZE) {
                // Try cleanup
                cleanupOldAttachments()
                if (getCacheSizeBytes() + availableBytes > MAX_CACHE_SIZE) {
                    return@withContext Result.Error(
                        Exception("Cache full. Please clear some space.")
                    )
                }
            }

            // Sanitize filename
            val safeFileName = sanitizeFileName(fileName)

            // Create email directory
            val emailDir = File(attachmentsDir, emailId).also { it.mkdirs() }

            // Use attachment ID as filename to prevent collisions
            val file = File(emailDir, "${attachmentId}_$safeFileName")

            // Save file
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            Result.Success(file)
        } catch (e: Exception) {
            Result.Error(e, "Failed to save attachment: ${e.message}")
        }
    }

    /**
     * Get attachment file if it exists
     */
    suspend fun getAttachmentFile(
        emailId: String,
        attachmentId: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val emailDir = File(attachmentsDir, emailId)
            if (!emailDir.exists()) return@withContext null

            // Find file starting with attachment ID
            emailDir.listFiles()?.find {
                it.name.startsWith("${attachmentId}_")
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get FileProvider URI for attachment
     */
    suspend fun getAttachmentUri(
        emailId: String,
        attachmentId: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = getAttachmentFile(emailId, attachmentId)
            if (file?.exists() == true) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete specific attachment
     */
    suspend fun deleteAttachment(
        emailId: String,
        attachmentId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getAttachmentFile(emailId, attachmentId)
            file?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete all attachments for an email
     */
    suspend fun deleteEmailAttachments(emailId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val emailDir = File(attachmentsDir, emailId)
            emailDir.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clean up old attachments
     */
    suspend fun cleanupOldAttachments(maxAgeDays: Int = MAX_AGE_DAYS) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val maxAge = maxAgeDays * 24 * 60 * 60 * 1000L

            attachmentsDir.listFiles()?.forEach { emailDir ->
                if (emailDir.isDirectory) {
                    emailDir.listFiles()?.forEach { file ->
                        if (now - file.lastModified() > maxAge) {
                            file.delete()
                        }
                    }

                    // Delete empty email directories
                    if (emailDir.listFiles()?.isEmpty() == true) {
                        emailDir.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't fail
        }
    }

    /**
     * Get total cache size in bytes
     */
    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            attachmentsDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Validate file size
     */
    fun validateFileSize(size: Long): Boolean {
        return size in 1..MAX_FILE_SIZE
    }

    /**
     * Sanitize filename to prevent security issues
     */
    private fun sanitizeFileName(fileName: String): String {
        var safe = fileName
            // Remove path traversal attempts
            .replace("..", "")
            .replace("/", "")
            .replace("\\", "")
            .replace("\u0000", "") // Null byte
            // Remove other control characters
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
            .trim()

        // Limit length
        if (safe.length > MAX_FILENAME_LENGTH) {
            val extension = safe.substringAfterLast('.', "")
            val name = safe.substringBeforeLast('.')
            val maxNameLength = MAX_FILENAME_LENGTH - extension.length - 1
            safe = name.take(maxNameLength) + if (extension.isNotEmpty()) ".$extension" else ""
        }

        // Fallback if name is empty
        if (safe.isEmpty() || safe == ".") {
            safe = "attachment_${UUID.randomUUID()}"
        }

        return safe
    }

    /**
     * Get MIME type from filename
     */
    fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "zip" -> "application/zip"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    /**
     * Check if file type is allowed
     */
    fun isAllowedFileType(fileName: String, mimeType: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        // Block dangerous file types
        val blockedExtensions = setOf(
            "exe", "bat", "sh", "app", "deb", "rpm", "apk",
            "msi", "dll", "scr", "vbs", "js", "jar", "com",
            "cmd", "ps1", "psm1"
        )

        if (extension in blockedExtensions) return false

        // Block executable MIME types
        val blockedMimeTypes = setOf(
            "application/x-executable",
            "application/x-msdownload",
            "application/x-sh",
            "application/x-bat"
        )

        if (mimeType in blockedMimeTypes) return false

        return true
    }
}
