package com.emailclient.presentation.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.emailclient.databinding.FragmentComposeBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for composing new emails
 */
@AndroidEntryPoint
class ComposeFragment : Fragment() {

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    private val args: ComposeFragmentArgs by navArgs()
    private val viewModel: ComposeViewModel by viewModels()

    private lateinit var attachmentAdapter: ComposeAttachmentAdapter

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            // Take persistable permission
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Permission not available, will still try to read
            }
            viewModel.addAttachment(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAttachments()
        setupButtons()
        observeViewModel()

        // Handle reply/forward if provided
        if (args.replyToEmailId != null) {
            viewModel.prepareReplyOrForward(
                emailId = args.replyToEmailId!!,
                isReplyAll = args.isReplyAll,
                isForward = args.isForward
            )
        }
    }

    private fun setupAttachments() {
        attachmentAdapter = ComposeAttachmentAdapter { attachmentId ->
            viewModel.removeAttachment(attachmentId)
        }
        binding.recyclerViewAttachments.adapter = attachmentAdapter
    }

    private fun setupButtons() {
        binding.btnSend.setOnClickListener {
            sendEmail()
        }

        binding.btnShowCcBcc.setOnClickListener {
            toggleCcBccVisibility()
        }

        binding.btnAttach.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }
    }

    private fun toggleCcBccVisibility() {
        val ccVisible = binding.layoutCc.visibility == View.VISIBLE
        val bccVisible = binding.layoutBcc.visibility == View.VISIBLE

        if (!ccVisible && !bccVisible) {
            // Show both CC and BCC
            binding.layoutCc.visibility = View.VISIBLE
            binding.layoutBcc.visibility = View.VISIBLE
        } else {
            // Hide both
            binding.layoutCc.visibility = View.GONE
            binding.layoutBcc.visibility = View.GONE
        }
    }

    private fun sendEmail() {
        // Validate fields
        val to = binding.editTo.text.toString().trim()
        val subject = binding.editSubject.text.toString().trim()
        val body = binding.editBody.text.toString()

        if (to.isEmpty()) {
            binding.layoutTo.error = "Please enter at least one recipient"
            return
        } else {
            binding.layoutTo.error = null
        }

        if (subject.isEmpty()) {
            binding.layoutSubject.error = "Please enter a subject"
            return
        } else {
            binding.layoutSubject.error = null
        }

        // Get CC if visible
        val cc = if (binding.layoutCc.visibility == View.VISIBLE) {
            binding.editCc.text.toString().trim()
        } else {
            ""
        }

        // Get BCC if visible
        val bcc = if (binding.layoutBcc.visibility == View.VISIBLE) {
            binding.editBcc.text.toString().trim()
        } else {
            ""
        }

        // Send email
        viewModel.sendEmail(
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body = body,
            isHtml = false
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is ComposeState.Idle -> {
                                binding.btnSend.isEnabled = true
                            }

                            is ComposeState.Sending -> {
                                binding.btnSend.isEnabled = false
                            }

                            is ComposeState.Success -> {
                                Snackbar.make(
                                    binding.root,
                                    "Email sent successfully!",
                                    Snackbar.LENGTH_SHORT
                                ).show()

                                // Navigate back
                                findNavController().navigateUp()
                            }

                            is ComposeState.Error -> {
                                binding.btnSend.isEnabled = true

                                Snackbar.make(
                                    binding.root,
                                    state.message,
                                    Snackbar.LENGTH_LONG
                                ).show()

                                viewModel.resetState()
                            }
                        }
                    }
                }

                // Observe compose data for pre-filling fields
                launch {
                    viewModel.composeData.collect { data ->
                        data?.let {
                            binding.editTo.setText(it.to)
                            binding.editSubject.setText(it.subject)
                            binding.editBody.setText(it.body)

                            // Show and populate CC if present
                            if (it.cc.isNotEmpty()) {
                                binding.layoutCc.visibility = View.VISIBLE
                                binding.layoutBcc.visibility = View.VISIBLE
                                binding.editCc.setText(it.cc)
                            }
                        }
                    }
                }

                // Observe attachments
                launch {
                    viewModel.attachments.collect { attachments ->
                        attachmentAdapter.submitList(attachments)
                        binding.recyclerViewAttachments.isVisible = attachments.isNotEmpty()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
