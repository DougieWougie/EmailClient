package com.emailclient.data.remote

import com.emailclient.domain.model.AuthenticationType
import com.emailclient.domain.model.SecurityType
import com.emailclient.domain.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for automatic email server configuration discovery
 */
@Singleton
class AutoDiscoveryService @Inject constructor() {

    /**
     * Result of auto-discovery
     */
    data class DiscoveredConfig(
        val imapConfig: ServerConfig,
        val smtpConfig: ServerConfig,
        val provider: String,
        val source: DiscoverySource,
        val supportsOAuth2: Boolean = false,
        val recommendedAuthType: AuthenticationType = AuthenticationType.PASSWORD
    )

    enum class DiscoverySource {
        KNOWN_PROVIDER,
        MOZILLA_AUTOCONFIG,
        AUTODISCOVER,
        MANUAL
    }

    /**
     * Attempt to discover email server settings for an email address
     */
    suspend fun discoverSettings(email: String): DiscoveredConfig? = withContext(Dispatchers.IO) {
        android.util.Log.d("AutoDiscovery", "Discovering settings for: $email")

        val domain = extractDomain(email)
        if (domain.isEmpty()) {
            android.util.Log.e("AutoDiscovery", "Invalid email address: $email")
            return@withContext null
        }

        // Try known providers first (fastest)
        getKnownProviderConfig(domain)?.let {
            android.util.Log.d("AutoDiscovery", "Found known provider config for: $domain")
            return@withContext it
        }

        // Try Mozilla Autoconfig
        tryMozillaAutoconfig(domain, email)?.let {
            android.util.Log.d("AutoDiscovery", "Found Mozilla autoconfig for: $domain")
            return@withContext it
        }

        // Try Microsoft Autodiscover
        tryAutodiscover(domain, email)?.let {
            android.util.Log.d("AutoDiscovery", "Found Autodiscover config for: $domain")
            return@withContext it
        }

        android.util.Log.w("AutoDiscovery", "No configuration found for: $domain")
        null
    }

    /**
     * Extract domain from email address
     */
    private fun extractDomain(email: String): String {
        return email.substringAfterLast("@", "").lowercase()
    }

    /**
     * Get configuration for known email providers
     */
    private fun getKnownProviderConfig(domain: String): DiscoveredConfig? {
        return when (domain) {
            // Gmail
            "gmail.com", "googlemail.com" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "imap.gmail.com",
                    port = 993,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.gmail.com",
                    port = 465,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                provider = "Gmail",
                source = DiscoverySource.KNOWN_PROVIDER
            )

            // Outlook.com / Hotmail / Live
            "outlook.com", "hotmail.com", "live.com", "msn.com" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "outlook.office365.com",
                    port = 993,
                    username = "",
                    securityType = SecurityType.SSL_TLS,
                    authenticationType = AuthenticationType.OAUTH2
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.office365.com",
                    port = 587,
                    username = "",
                    securityType = SecurityType.STARTTLS,
                    authenticationType = AuthenticationType.OAUTH2
                ),
                provider = "Outlook",
                source = DiscoverySource.KNOWN_PROVIDER,
                supportsOAuth2 = true,
                recommendedAuthType = AuthenticationType.OAUTH2
            )

            // Yahoo
            "yahoo.com", "ymail.com", "rocketmail.com" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "imap.mail.yahoo.com",
                    port = 993,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.mail.yahoo.com",
                    port = 465,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                provider = "Yahoo",
                source = DiscoverySource.KNOWN_PROVIDER
            )

            // iCloud
            "icloud.com", "me.com", "mac.com" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "imap.mail.me.com",
                    port = 993,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.mail.me.com",
                    port = 587,
                    username = "",
                    securityType = SecurityType.STARTTLS
                ),
                provider = "iCloud",
                source = DiscoverySource.KNOWN_PROVIDER
            )

            // AOL
            "aol.com", "aim.com" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "imap.aol.com",
                    port = 993,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.aol.com",
                    port = 465,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                provider = "AOL",
                source = DiscoverySource.KNOWN_PROVIDER
            )

            // ProtonMail (Bridge required)
            "protonmail.com", "proton.me", "pm.me" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "127.0.0.1",
                    port = 1143,
                    username = "",
                    securityType = SecurityType.STARTTLS
                ),
                smtpConfig = ServerConfig(
                    host = "127.0.0.1",
                    port = 1025,
                    username = "",
                    securityType = SecurityType.STARTTLS
                ),
                provider = "ProtonMail",
                source = DiscoverySource.KNOWN_PROVIDER
            )

            // Zoho Mail
            "zoho.com", "zohomail.com" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "imap.zoho.com",
                    port = 993,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.zoho.com",
                    port = 465,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                provider = "Zoho",
                source = DiscoverySource.KNOWN_PROVIDER
            )

            // GMX
            "gmx.com", "gmx.net", "gmx.de" -> DiscoveredConfig(
                imapConfig = ServerConfig(
                    host = "imap.gmx.com",
                    port = 993,
                    username = "",
                    securityType = SecurityType.SSL_TLS
                ),
                smtpConfig = ServerConfig(
                    host = "smtp.gmx.com",
                    port = 587,
                    username = "",
                    securityType = SecurityType.STARTTLS
                ),
                provider = "GMX",
                source = DiscoverySource.KNOWN_PROVIDER
            )

            else -> null
        }
    }

    /**
     * Try Mozilla Thunderbird Autoconfig
     * https://developer.mozilla.org/en-US/docs/Mozilla/Thunderbird/Autoconfiguration
     */
    private suspend fun tryMozillaAutoconfig(domain: String, email: String): DiscoveredConfig? {
        val urls = listOf(
            "https://autoconfig.$domain/mail/config-v1.1.xml?emailaddress=$email",
            "https://$domain/.well-known/autoconfig/mail/config-v1.1.xml",
            "https://autoconfig.thunderbird.net/v1.1/$domain"
        )

        for (url in urls) {
            try {
                android.util.Log.d("AutoDiscovery", "Trying Mozilla autoconfig: $url")
                val config = fetchAndParseMozillaConfig(url)
                if (config != null) {
                    return config
                }
            } catch (e: Exception) {
                android.util.Log.d("AutoDiscovery", "Mozilla autoconfig failed for $url: ${e.message}")
            }
        }

        return null
    }

    /**
     * Fetch and parse Mozilla autoconfig XML
     */
    private suspend fun fetchAndParseMozillaConfig(urlString: String): DiscoveredConfig? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "EmailClient/1.0")

            if (connection.responseCode == 200) {
                val xml = connection.inputStream.bufferedReader().use { it.readText() }
                parseMozillaXml(xml)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.d("AutoDiscovery", "Failed to fetch Mozilla config: ${e.message}")
            null
        }
    }

    /**
     * Parse Mozilla autoconfig XML
     * This is a simple parser - a full implementation would use an XML parser
     */
    private fun parseMozillaXml(xml: String): DiscoveredConfig? {
        return try {
            var imapHost = ""
            var imapPort = 993
            var imapSecurity = SecurityType.SSL_TLS

            var smtpHost = ""
            var smtpPort = 465
            var smtpSecurity = SecurityType.SSL_TLS

            // Simple regex-based parsing (production should use proper XML parser)
            val imapServerMatch = Regex("<incomingServer type=\"imap\">.*?</incomingServer>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.value

            if (imapServerMatch != null) {
                imapHost = Regex("<hostname>(.*?)</hostname>").find(imapServerMatch)?.groupValues?.get(1) ?: ""
                imapPort = Regex("<port>(.*?)</port>").find(imapServerMatch)?.groupValues?.get(1)?.toIntOrNull() ?: 993
                val socketType = Regex("<socketType>(.*?)</socketType>").find(imapServerMatch)?.groupValues?.get(1)
                imapSecurity = when (socketType?.uppercase()) {
                    "SSL" -> SecurityType.SSL_TLS
                    "STARTTLS" -> SecurityType.STARTTLS
                    else -> SecurityType.SSL_TLS
                }
            }

            val smtpServerMatch = Regex("<outgoingServer type=\"smtp\">.*?</outgoingServer>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.value

            if (smtpServerMatch != null) {
                smtpHost = Regex("<hostname>(.*?)</hostname>").find(smtpServerMatch)?.groupValues?.get(1) ?: ""
                smtpPort = Regex("<port>(.*?)</port>").find(smtpServerMatch)?.groupValues?.get(1)?.toIntOrNull() ?: 587
                val socketType = Regex("<socketType>(.*?)</socketType>").find(smtpServerMatch)?.groupValues?.get(1)
                smtpSecurity = when (socketType?.uppercase()) {
                    "SSL" -> SecurityType.SSL_TLS
                    "STARTTLS" -> SecurityType.STARTTLS
                    else -> SecurityType.STARTTLS
                }
            }

            if (imapHost.isNotEmpty() && smtpHost.isNotEmpty()) {
                DiscoveredConfig(
                    imapConfig = ServerConfig(
                        host = imapHost,
                        port = imapPort,
                        username = "",
                        securityType = imapSecurity
                    ),
                    smtpConfig = ServerConfig(
                        host = smtpHost,
                        port = smtpPort,
                        username = "",
                        securityType = smtpSecurity
                    ),
                    provider = "Autoconfig",
                    source = DiscoverySource.MOZILLA_AUTOCONFIG
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AutoDiscovery", "Failed to parse Mozilla XML: ${e.message}")
            null
        }
    }

    /**
     * Try Microsoft Autodiscover
     * Basic implementation - full support would require OAuth2
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun tryAutodiscover(domain: String, email: String): DiscoveredConfig? {
        // Microsoft Autodiscover is complex and typically requires authentication
        // For now, we'll just check if it's an Office 365 domain
        val urls = listOf(
            "https://autodiscover.$domain/autodiscover/autodiscover.xml",
            "https://$domain/autodiscover/autodiscover.xml"
        )

        for (url in urls) {
            try {
                android.util.Log.d("AutoDiscovery", "Trying Autodiscover: $url")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // If the autodiscover endpoint exists, assume Office 365
                if (connection.responseCode in 200..499) {
                    return getKnownProviderConfig("outlook.com")?.copy(
                        provider = "Office 365",
                        source = DiscoverySource.AUTODISCOVER
                    )
                }
            } catch (e: Exception) {
                android.util.Log.d("AutoDiscovery", "Autodiscover failed for $url: ${e.message}")
            }
        }

        return null
    }
}
