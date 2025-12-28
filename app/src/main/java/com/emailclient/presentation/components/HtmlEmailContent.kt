package com.emailclient.presentation.components

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    AndroidView(
        factory = { context ->
            WebView(context).apply {
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
                        // Handle link clicks - could be expanded to open in browser
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
