package com.emailclient.presentation.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
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

        setupWebView()
        setupButtons()
        observeViewModel()

        // Load email
        viewModel.loadEmail(args.emailId)
    }

    private fun setupWebView() {
        binding.webViewBody.settings.apply {
            javaScriptEnabled = false // Disable JavaScript for security
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
        }
    }

    private fun setupButtons() {
        binding.btnReply.setOnClickListener {
            val email = viewModel.email.value ?: return@setOnClickListener
            val action = EmailDetailFragmentDirections.actionDetailToCompose(
                replyToEmailId = email.id,
                isReplyAll = false,
                isForward = false
            )
            findNavController().navigate(action)
        }

        binding.btnReplyAll.setOnClickListener {
            val email = viewModel.email.value ?: return@setOnClickListener
            val action = EmailDetailFragmentDirections.actionDetailToCompose(
                replyToEmailId = email.id,
                isReplyAll = true,
                isForward = false
            )
            findNavController().navigate(action)
        }

        binding.btnForward.setOnClickListener {
            val email = viewModel.email.value ?: return@setOnClickListener
            val action = EmailDetailFragmentDirections.actionDetailToCompose(
                replyToEmailId = email.id,
                isReplyAll = false,
                isForward = true
            )
            findNavController().navigate(action)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.email.collect { email ->
                        email?.let { displayEmail(it) }
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
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

        // Display email body
        if (email.isHtml && email.htmlBody != null) {
            // Load HTML content
            binding.webViewBody.loadDataWithBaseURL(
                null,
                wrapHtmlContent(email.htmlBody),
                "text/html",
                "UTF-8",
                null
            )
        } else {
            // Load plain text as HTML
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
     * Wrap HTML content with proper styling
     */
    private fun wrapHtmlContent(html: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: sans-serif;
                        font-size: 14px;
                        margin: 0;
                        padding: 0;
                        word-wrap: break-word;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                    }
                    a {
                        color: #1976D2;
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
     * Wrap plain text content as HTML
     */
    private fun wrapPlainTextContent(text: String): String {
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
                <style>
                    body {
                        font-family: monospace;
                        font-size: 14px;
                        margin: 0;
                        padding: 0;
                        white-space: pre-wrap;
                        word-wrap: break-word;
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
