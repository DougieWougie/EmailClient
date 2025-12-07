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
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
                }
                SecurityType.STARTTLS -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                }
                SecurityType.NONE -> {
                    // No encryption
                }
            }

            // Connection settings
            put("mail.smtp.connectiontimeout", "10000") // 10 seconds
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
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
        try {
            val session = createSession(account, password)
            val transport = session.getTransport("smtp")

            transport.connect(
                account.smtpConfig.host,
                account.smtpConfig.port,
                account.smtpConfig.username,
                password
            )

            val isConnected = transport.isConnected
            transport.close()

            isConnected
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
