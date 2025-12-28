package com.emailclient.presentation.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Composable that displays HTML email content using WebView.
 * Maintains all security settings from the original EmailDetailFragment implementation.
 *
 * @param htmlContent The sanitized HTML content to display
 * @param allowExternalImages Whether to allow loading external images
 * @param modifier Modifier for the WebView
 */
@Composable
fun HtmlEmailContent(
    htmlContent: String,
    allowExternalImages: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { factoryContext ->
            WebView(factoryContext).apply {
                settings.apply {
                    // Security: Disable JavaScript
                    javaScriptEnabled = false

                    // Security: Disable file access
                    allowFileAccess = false
                    allowContentAccess = false

                    // Block network resources by default (privacy and security)
                    blockNetworkImage = !allowExternalImages
                    blockNetworkLoads = !allowExternalImages

                    // Enable zoom controls
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false

                    // Mixed content
                    @Suppress("DEPRECATION")
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // Safe browsing
                    safeBrowsingEnabled = true
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        // Security: Validate URL scheme before opening
                        request?.url?.let { uri ->
                            when (uri.scheme?.lowercase()) {
                                "http", "https" -> {
                                    // Open HTTP/HTTPS links in external browser
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("HtmlEmailContent", "Failed to open URL", e)
                                    }
                                }
                                "mailto" -> {
                                    // Open mailto links in email app
                                    try {
                                        val intent = Intent(Intent.ACTION_SENDTO, uri)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("HtmlEmailContent", "Failed to open mailto", e)
                                    }
                                }
                                else -> {
                                    // Block all other schemes (javascript:, file:, data:, etc.)
                                    android.util.Log.w("HtmlEmailContent",
                                        "Blocked non-standard URL scheme: ${uri.scheme}")
                                }
                            }
                        }
                        // Always return true to prevent WebView from loading the URL
                        return true
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                null,
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
            webView.settings.blockNetworkImage = !allowExternalImages
            webView.settings.blockNetworkLoads = !allowExternalImages
        },
        modifier = modifier
    )
}
