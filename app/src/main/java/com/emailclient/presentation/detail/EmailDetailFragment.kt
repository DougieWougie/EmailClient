package com.emailclient.presentation.detail

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.emailclient.R
import com.emailclient.databinding.FragmentEmailDetailBinding
import com.emailclient.domain.model.Email
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fragment displaying email details
 */
@AndroidEntryPoint
class EmailDetailFragment : Fragment() {

    private var _binding: FragmentEmailDetailBinding? = null
    private val binding get() = _binding!!

    private val args: EmailDetailFragmentArgs by navArgs()
    private val viewModel: EmailDetailViewModel by viewModels()

    private val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    private var currentMenu: Menu? = null

    private val attachmentAdapter = AttachmentAdapter(
        onDownloadClick = { attachmentId ->
            viewModel.downloadAttachment(attachmentId)
        },
        onOpenClick = { attachmentId ->
            viewModel.openAttachment(attachmentId)
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmailDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupWebView()
        setupLoadImagesButton()
        setupAttachments()
        observeViewModel()

        // Load email
        viewModel.loadEmail(args.emailId)
    }

    private fun setupAttachments() {
        binding.recyclerViewAttachments.adapter = attachmentAdapter
    }

    private fun setupMenu() {
        // Add menu to the existing activity toolbar
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_email_detail, menu)
                currentMenu = menu
                updateMenuItemsState()
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return handleMenuItemClick(menuItem)
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun updateMenuItemsState() {
        val hasEmail = viewModel.email.value != null
        currentMenu?.apply {
            findItem(R.id.action_reply)?.isEnabled = hasEmail
            findItem(R.id.action_reply_all)?.isEnabled = hasEmail
            findItem(R.id.action_forward)?.isEnabled = hasEmail
            findItem(R.id.action_move)?.isEnabled = hasEmail
            findItem(R.id.action_archive_email)?.isEnabled = hasEmail
            findItem(R.id.action_delete_email)?.isEnabled = hasEmail
        }
    }

    private fun handleMenuItemClick(menuItem: MenuItem): Boolean {
        val email = viewModel.email.value ?: return false

        return when (menuItem.itemId) {
            R.id.action_reply -> {
                val action = EmailDetailFragmentDirections.actionDetailToCompose(
                    replyToEmailId = email.id,
                    isReplyAll = false,
                    isForward = false
                )
                findNavController().navigate(action)
                true
            }
            R.id.action_reply_all -> {
                val action = EmailDetailFragmentDirections.actionDetailToCompose(
                    replyToEmailId = email.id,
                    isReplyAll = true,
                    isForward = false
                )
                findNavController().navigate(action)
                true
            }
            R.id.action_forward -> {
                val action = EmailDetailFragmentDirections.actionDetailToCompose(
                    replyToEmailId = email.id,
                    isReplyAll = false,
                    isForward = true
                )
                findNavController().navigate(action)
                true
            }
            R.id.action_move -> {
                showMoveToFolderDialog(email.id)
                true
            }
            R.id.action_archive_email -> {
                viewModel.archiveEmail(email.id)
                true
            }
            R.id.action_delete_email -> {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete Email")
                    .setMessage("Are you sure you want to delete this email?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteEmail(email.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> false
        }
    }

    private fun setupWebView() {
        binding.webViewBody.apply {
            settings.apply {
                // Security settings
                javaScriptEnabled = false // Disable JavaScript for security
                allowFileAccess = false // Prevent file access
                allowContentAccess = false // Prevent content provider access
                setSupportMultipleWindows(false)

                // Display settings
                loadWithOverviewMode = false
                useWideViewPort = false
                builtInZoomControls = false
                displayZoomControls = false

                // Mixed content and safe browsing
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // Block network access for privacy and security (will be toggled dynamically)
                blockNetworkImage = true
                blockNetworkLoads = true
            }

            // Enable dynamic height adjustment
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Adjust height to fit content
                    view?.evaluateJavascript(
                        "(function() { return document.body.scrollHeight; })();"
                    ) { _ ->
                        // Height will be returned as string, parse and update layout
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean {
                    // Handle link clicks - open in browser instead of WebView
                    request?.url?.let { uri ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                    return true // Prevent WebView from loading the URL
                }
            }
        }
    }

    /**
     * Setup Load Images button
     */
    private fun setupLoadImagesButton() {
        binding.buttonLoadImages.setOnClickListener {
            viewModel.loadImages()
        }
    }

    /**
     * Enable image loading in WebView
     */
    private fun enableImageLoading() {
        binding.webViewBody.settings.apply {
            blockNetworkImage = false
            blockNetworkLoads = false
        }

        // Reload the current email to show images
        viewModel.email.value?.let { email ->
            displayEmail(email, allowExternalImages = true)
        }
    }

    private fun showMoveToFolderDialog(emailId: String) {
        val dialogView = layoutInflater.inflate(com.emailclient.R.layout.dialog_move_to_folder, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.emailclient.R.id.recyclerViewFolders)
        val progressBar = dialogView.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(com.emailclient.R.id.progressBar)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Create simple folder adapter for selection
        val folderAdapter = SimpleFolderAdapter { folder ->
            viewModel.moveEmail(emailId, folder.id)
            dialog.dismiss()
        }

        recyclerView.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            adapter = folderAdapter
        }

        // Load folders
        progressBar.visibility = android.view.View.VISIBLE
        viewModel.loadFolders()

        // Observe folders
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.folders.collect { folders ->
                if (folders.isNotEmpty()) {
                    progressBar.visibility = android.view.View.GONE
                    folderAdapter.submitList(folders)
                }
            }
        }

        dialog.show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.email.collect { email ->
                        email?.let {
                            displayEmail(it)
                            updateMenuItemsState()
                        }
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }

                launch {
                    viewModel.actionResult.collect { result ->
                        result?.let {
                            when {
                                it.contains("deleted", ignoreCase = true) ||
                                it.contains("archived", ignoreCase = true) -> {
                                    // Show undo snackbar for delete/archive
                                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG)
                                        .setAction("UNDO") {
                                            viewModel.undoLastAction()
                                        }
                                        .addCallback(object : Snackbar.Callback() {
                                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                                if (event != DISMISS_EVENT_ACTION) {
                                                    // Undo was not clicked, finalize the action
                                                    viewModel.finalizeAction()
                                                    findNavController().navigateUp()
                                                }
                                            }
                                        })
                                        .show()
                                    viewModel.clearActionResult()
                                }
                                it.contains("moved", ignoreCase = true) -> {
                                    // Simple snackbar for move
                                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                                    viewModel.clearActionResult()
                                    findNavController().navigateUp()
                                }
                                else -> {
                                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                                    viewModel.clearActionResult()
                                }
                            }
                        }
                    }
                }

                // Observe load images button visibility
                launch {
                    viewModel.shouldShowLoadImagesButton.collect { shouldShow ->
                        binding.cardLoadImages.visibility = if (shouldShow) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }

                // Observe image loading state
                launch {
                    viewModel.imagesLoaded.collect { loaded ->
                        if (loaded) {
                            enableImageLoading()
                        }
                    }
                }

                // Observe auto-download setting
                launch {
                    viewModel.autoDownloadImagesEnabled.collect { autoDownload ->
                        if (autoDownload) {
                            enableImageLoading()
                        }
                    }
                }

                // Observe attachment states
                launch {
                    viewModel.attachmentStates.collect { states ->
                        if (states.isEmpty()) {
                            binding.recyclerViewAttachments.visibility = View.GONE
                        } else {
                            binding.recyclerViewAttachments.visibility = View.VISIBLE
                            attachmentAdapter.submitList(states.values.toList())
                        }
                    }
                }

                // Observe attachment actions
                launch {
                    viewModel.attachmentAction.collect { action ->
                        when (action) {
                            is EmailDetailViewModel.AttachmentAction.Open -> {
                                openFile(action.file)
                                viewModel.clearAttachmentAction()
                            }
                            is EmailDetailViewModel.AttachmentAction.Error -> {
                                Snackbar.make(binding.root, action.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearAttachmentAction()
                            }
                            null -> {
                                // No action
                            }
                        }
                    }
                }
            }
        }
    }

    private fun displayEmail(email: Email, allowExternalImages: Boolean = false) {
        binding.textSubject.text = email.subject
        binding.textFrom.text = email.from.let {
            if (it.personal != null) "${it.personal} <${it.address}>" else it.address
        }
        binding.textTo.text = email.to.joinToString(", ") {
            if (it.personal != null) "${it.personal} <${it.address}>" else it.address
        }
        binding.textDate.text = dateFormat.format(email.receivedDate)

        // Display email body - auto-detect HTML even if not flagged
        val htmlContent = email.htmlBody ?: email.body
        val hasHtmlTags = containsHtmlTags(htmlContent)
        val shouldRenderAsHtml = email.isHtml || hasHtmlTags

        android.util.Log.d("EmailDetail", "Displaying email: ${email.subject}")
        android.util.Log.d("EmailDetail", "isHtml flag: ${email.isHtml}, hasHtmlTags: $hasHtmlTags, shouldRenderAsHtml: $shouldRenderAsHtml")
        android.util.Log.d("EmailDetail", "Content preview: ${htmlContent.take(100)}")
        android.util.Log.d("EmailDetail", "allowExternalImages: $allowExternalImages")

        if (shouldRenderAsHtml) {
            // Load HTML content
            android.util.Log.d("EmailDetail", "Rendering as HTML")
            binding.webViewBody.loadDataWithBaseURL(
                null,
                wrapHtmlContent(htmlContent, allowExternalImages),
                "text/html",
                "UTF-8",
                null
            )
        } else {
            // Load plain text as HTML
            android.util.Log.d("EmailDetail", "Rendering as plain text")
            binding.webViewBody.loadDataWithBaseURL(
                null,
                wrapPlainTextContent(email.body),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    /**
     * Detect if content contains HTML tags (ReDoS-safe implementation)
     */
    private fun containsHtmlTags(content: String): Boolean {
        // Use simple string matching instead of complex regex to avoid ReDoS
        return content.contains("<div", ignoreCase = true) ||
               content.contains("<p>", ignoreCase = true) ||
               content.contains("<p ", ignoreCase = true) ||
               content.contains("<br", ignoreCase = true) ||
               content.contains("<span", ignoreCase = true) ||
               content.contains("<table", ignoreCase = true) ||
               content.contains("<html", ignoreCase = true) ||
               content.contains("<body", ignoreCase = true) ||
               content.contains("</div>", ignoreCase = true) ||
               content.contains("</p>", ignoreCase = true)
    }

    /**
     * Wrap HTML content with proper styling and sanitization
     */
    private fun wrapHtmlContent(html: String, allowExternalImages: Boolean = false): String {
        // Sanitize HTML using JSoup to prevent XSS attacks
        val sanitized = Jsoup.clean(
            html,
            Safelist.relaxed()
                .addTags("div", "span", "pre", "code", "hr")
                .addAttributes("a", "href", "title")
                .addAttributes("img", "src", "alt", "title", "width", "height")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https", "data")
        )

        val isDarkMode = isDarkMode()
        val bgColor = if (isDarkMode) {
            getColorHex(R.color.email_webview_dark_background)
        } else {
            getColorHex(R.color.email_webview_light_background)
        }
        val textColor = if (isDarkMode) {
            getColorHex(R.color.email_webview_dark_text)
        } else {
            getColorHex(R.color.email_webview_light_text)
        }
        val linkColor = if (isDarkMode) {
            getColorHex(R.color.email_webview_dark_link)
        } else {
            getColorHex(R.color.email_webview_light_link)
        }

        // Conditionally allow external images in CSP
        val imgSrc = if (allowExternalImages) {
            "img-src http: https: data:"
        } else {
            "img-src data:"
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="${if (isDarkMode) "dark" else "light"}">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; $imgSrc; style-src 'unsafe-inline';">
                <style>
                    body {
                        font-family: sans-serif;
                        font-size: 14px;
                        margin: 0;
                        padding: 0;
                        word-wrap: break-word;
                        background-color: $bgColor;
                        color: $textColor;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                    }
                    a {
                        color: $linkColor;
                        text-decoration: underline;
                    }
                    blockquote {
                        border-left: 3px solid ${if (isDarkMode) getColorHex(R.color.email_webview_dark_blockquote_border) else getColorHex(R.color.email_webview_light_blockquote_border)};
                        margin-left: 0;
                        padding-left: 16px;
                        color: ${if (isDarkMode) getColorHex(R.color.email_webview_dark_blockquote_text) else getColorHex(R.color.email_webview_light_blockquote_text)};
                    }
                    pre, code {
                        background-color: ${if (isDarkMode) getColorHex(R.color.email_webview_dark_code_bg) else getColorHex(R.color.email_webview_light_code_bg)};
                        color: ${if (isDarkMode) getColorHex(R.color.email_webview_dark_code_text) else getColorHex(R.color.email_webview_light_code_text)};
                        padding: 4px 8px;
                        border-radius: 4px;
                        overflow-x: auto;
                    }
                    table {
                        border-collapse: collapse;
                        max-width: 100%;
                    }
                    th, td {
                        padding: 8px;
                    }
                    * {
                        max-width: 100%;
                    }
                </style>
            </head>
            <body>
                $sanitized
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Check if dark mode is enabled
     */
    private fun isDarkMode(): Boolean {
        return when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    /**
     * Get color from resources as hex string for CSS
     */
    private fun getColorHex(colorResId: Int): String {
        val color = androidx.core.content.ContextCompat.getColor(requireContext(), colorResId)
        return String.format("#%06X", 0xFFFFFF and color)
    }

    /**
     * Wrap plain text content as HTML
     */
    private fun wrapPlainTextContent(text: String): String {
        val isDarkMode = isDarkMode()
        val bgColor = if (isDarkMode) {
            getColorHex(R.color.email_webview_dark_background)
        } else {
            getColorHex(R.color.email_webview_light_background)
        }
        val textColor = if (isDarkMode) {
            getColorHex(R.color.email_webview_dark_text)
        } else {
            getColorHex(R.color.email_webview_light_text)
        }

        val escapedText = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="${if (isDarkMode) "dark" else "light"}">
                <style>
                    body {
                        font-family: monospace;
                        font-size: 14px;
                        margin: 0;
                        padding: 0;
                        white-space: pre-wrap;
                        word-wrap: break-word;
                        background-color: $bgColor;
                        color: $textColor;
                    }
                </style>
            </head>
            <body>
                $escapedText
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Open attachment file with appropriate app
     */
    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(Intent.createChooser(intent, getString(R.string.open_attachment)))
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.no_app_to_open),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Snackbar.make(
                binding.root,
                "Error opening file: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Get MIME type for file
     */
    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
