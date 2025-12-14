package com.emailclient.data.remote.smtp

import com.emailclient.domain.model.Account
import com.emailclient.domain.model.SecurityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Service for SMTP email sending using JavaMail
 */
@Singleton
class SMTPService @Inject constructor() {

    /**
     * Send an email via SMTP
     */
    suspend fun sendEmail(
        account: Account,
        password: String,
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val session = createSession(account, password)
            val message = createMessage(session, account, to, cc, bcc, subject, body, isHtml)

            Transport.send(message)
            true
        } catch (e: AuthenticationFailedException) {
            android.util.Log.e("SMTPService", "✗ SMTP Authentication FAILED", e)
            throw Exception("SMTP Authentication failed. Please check your email and password.", e)
        } catch (e: SendFailedException) {
            android.util.Log.e("SMTPService", "✗ SMTP Send FAILED", e)
            val message = when {
                e.message?.contains("Invalid Addresses") == true ->
                    "One or more recipient addresses are invalid. Please check the email addresses."
                e.message?.contains("550") == true ->
                    "The recipient's mail server rejected the email. Please check the recipient address."
                else -> "Failed to send email: ${e.message ?: "Unknown error"}"
            }
            throw Exception(message, e)
        } catch (e: MessagingException) {
            android.util.Log.e("SMTPService", "✗ SMTP MessagingException", e)
            val message = when {
                e.message?.contains("Unknown host") == true ->
                    "Cannot find SMTP server '${account.smtpConfig.host}'. Please check the server address."
                e.message?.contains("Connection refused") == true || e.message?.contains("failed") == true ->
                    "Cannot connect to SMTP server '${account.smtpConfig.host}:${account.smtpConfig.port}'. Please check the server and port."
                e.message?.contains("timeout") == true ->
                    "Connection to SMTP server timed out. Please check your internet connection."
                else -> "SMTP error: ${e.message ?: "Unknown error"}"
            }
            throw Exception(message, e)
        } catch (e: Exception) {
            android.util.Log.e("SMTPService", "✗ SMTP General Exception", e)
            throw Exception("Failed to send email: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * Create SMTP session with authentication
     */
    private fun createSession(account: Account, password: String): Session {
        val props = Properties().apply {
            put("mail.smtp.host", account.smtpConfig.host)
            put("mail.smtp.port", account.smtpConfig.port.toString())
            put("mail.smtp.auth", "true")

            when (account.smtpConfig.securityType) {
                SecurityType.SSL_TLS -> {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                }
                SecurityType.STARTTLS -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                }
                SecurityType.NONE -> {
                    throw SecurityException(
                        "Unencrypted connections are not allowed for security reasons. " +
                        "Please use SSL/TLS or STARTTLS."
                    )
                }
            }

            // Connection settings - increased for slow/unreliable connections
            put("mail.smtp.connectiontimeout", "90000") // 90 seconds
            put("mail.smtp.timeout", "90000") // 90 seconds
            put("mail.smtp.writetimeout", "90000") // 90 seconds

            // Authentication settings - prefer LOGIN over AUTHENTICATE PLAIN
            // Some mail servers have issues with AUTHENTICATE PLAIN
            put("mail.smtp.auth.login.disable", "false") // Enable LOGIN
            put("mail.smtp.auth.plain.disable", "true") // Disable PLAIN to force LOGIN

            // Android-specific settings
            put("mail.smtp.ssl.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.ssl.socketFactory.fallback", "false")
        }

        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(account.smtpConfig.username, password)
            }
        })
    }

    /**
     * Create email message
     */
    private fun createMessage(
        session: Session,
        account: Account,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        body: String,
        isHtml: Boolean
    ): Message {
        val message = MimeMessage(session)

        // From
        message.setFrom(InternetAddress(account.email, account.displayName))

        // To
        to.forEach { address ->
            message.addRecipient(
                Message.RecipientType.TO,
                InternetAddress(address)
            )
        }

        // CC
        cc.forEach { address ->
            message.addRecipient(
                Message.RecipientType.CC,
                InternetAddress(address)
            )
        }

        // BCC
        bcc.forEach { address ->
            message.addRecipient(
                Message.RecipientType.BCC,
                InternetAddress(address)
            )
        }

        // Subject
        message.subject = subject

        // Body
        if (isHtml) {
            message.setContent(body, "text/html; charset=utf-8")
        } else {
            message.setText(body, "utf-8")
        }

        // Sent date
        message.sentDate = Date()

        return message
    }

    /**
     * Test SMTP connection
     */
    suspend fun testConnection(account: Account, password: String): Boolean = withContext(Dispatchers.IO) {
        var transport: Transport? = null
        try {
            android.util.Log.d("SMTPService", "Testing SMTP connection to ${account.smtpConfig.host}:${account.smtpConfig.port} with ${account.smtpConfig.securityType}")

            val session = createSession(account, password)
            transport = session.getTransport("smtp")

            android.util.Log.d("SMTPService", "Connecting to SMTP server...")
            transport.connect(
                account.smtpConfig.host,
                account.smtpConfig.port,
                account.smtpConfig.username,
                password
            )

            val isConnected = transport.isConnected
            android.util.Log.d("SMTPService", "SMTP connection result: $isConnected")
            isConnected
        } catch (e: AuthenticationFailedException) {
            e.printStackTrace()
            throw Exception("SMTP Authentication failed. Please check your email and password.", e)
        } catch (e: MessagingException) {
            e.printStackTrace()
            val message = when {
                e.message?.contains("Unknown host") == true ->
                    "Cannot find SMTP server '${account.smtpConfig.host}'. Please check the server address."
                e.message?.contains("Connection refused") == true || e.message?.contains("failed") == true ->
                    "Cannot connect to SMTP server '${account.smtpConfig.host}:${account.smtpConfig.port}'. Please check the server and port."
                e.message?.contains("timeout") == true ->
                    "Connection to SMTP server timed out. Please check your internet connection."
                else -> "SMTP connection failed: ${e.message ?: "Unknown error"}"
            }
            throw Exception(message, e)
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("SMTP connection failed: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            try {
                transport?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }
}
