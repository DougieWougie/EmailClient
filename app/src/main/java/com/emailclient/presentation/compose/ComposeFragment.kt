package com.emailclient.presentation.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        setupButtons()
        observeViewModel()
    }

    private fun setupButtons() {
        binding.btnSend.setOnClickListener {
            sendEmail()
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

        // Send email
        viewModel.sendEmail(
            to = to,
            cc = cc,
            subject = subject,
            body = body,
            isHtml = false
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
