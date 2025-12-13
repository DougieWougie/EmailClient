package com.emailclient.presentation.detail

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
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
        observeViewModel()

        // Load email
        viewModel.loadEmail(args.emailId)
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
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false

                // Mixed content and safe browsing
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // Remote images - block by default for privacy/security
                blockNetworkImage = false // Set to true to block remote images
                blockNetworkLoads = false // Set to true to block all network requests
            }

            // Enable dynamic height adjustment
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Adjust height to fit content
                    view?.evaluateJavascript(
                        "(function() { return document.body.scrollHeight; })();"
                    ) { height ->
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
            }
        }
    }

    private fun displayEmail(email: Email) {
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

        if (shouldRenderAsHtml) {
            // Load HTML content
            android.util.Log.d("EmailDetail", "Rendering as HTML")
            binding.webViewBody.loadDataWithBaseURL(
                null,
                wrapHtmlContent(htmlContent),
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
     * Detect if content contains HTML tags
     */
    private fun containsHtmlTags(content: String): Boolean {
        // Check for common HTML tags
        val htmlTagPattern = Regex(
            "<(div|p|br|span|a|img|table|tr|td|th|h[1-6]|ul|ol|li|strong|em|b|i|u|html|body|head)[\\s>]",
            RegexOption.IGNORE_CASE
        )
        return htmlTagPattern.containsMatchIn(content)
    }

    /**
     * Wrap HTML content with proper styling
     */
    private fun wrapHtmlContent(html: String): String {
        val isDarkMode = isDarkMode()
        val bgColor = if (isDarkMode) "#121212" else "#FFFFFF"
        val textColor = if (isDarkMode) "#E0E0E0" else "#000000"
        val linkColor = if (isDarkMode) "#64B5F6" else "#1976D2"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="${if (isDarkMode) "dark" else "light"}">
                <style>
                    body {
                        font-family: sans-serif;
                        font-size: 14px;
                        margin: 16px;
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
                        border-left: 3px solid ${if (isDarkMode) "#555" else "#ccc"};
                        margin-left: 0;
                        padding-left: 16px;
                        color: ${if (isDarkMode) "#aaa" else "#666"};
                    }
                    pre, code {
                        background-color: ${if (isDarkMode) "#1E1E1E" else "#F5F5F5"};
                        color: ${if (isDarkMode) "#D4D4D4" else "#333"};
                        padding: 4px 8px;
                        border-radius: 4px;
                        overflow-x: auto;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                    }
                    th, td {
                        border: 1px solid ${if (isDarkMode) "#444" else "#ddd"};
                        padding: 8px;
                        text-align: left;
                    }
                    th {
                        background-color: ${if (isDarkMode) "#2A2A2A" else "#F5F5F5"};
                    }
                </style>
            </head>
            <body>
                $html
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
     * Wrap plain text content as HTML
     */
    private fun wrapPlainTextContent(text: String): String {
        val isDarkMode = isDarkMode()
        val bgColor = if (isDarkMode) "#121212" else "#FFFFFF"
        val textColor = if (isDarkMode) "#E0E0E0" else "#000000"

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
                        margin: 16px;
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
