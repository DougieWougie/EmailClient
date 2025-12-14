package com.emailclient.presentation.setup

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.transition.Explode
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.emailclient.R
import com.emailclient.data.local.AppPreferences
import com.emailclient.databinding.ActivityAccountSetupBinding
import com.emailclient.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity for account setup wizard
 */
@AndroidEntryPoint
class AccountSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSetupBinding

    @Inject
    lateinit var appPreferences: AppPreferences

    private val viewModel: AccountSetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Setup transitions if animations are enabled
        // Note: Must check preferences directly before super.onCreate() since Hilt injection happens in super
        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        val animationsEnabled = prefs.getBoolean("animations_enabled", true)

        if (animationsEnabled) {
            with(window) {
                requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
                enterTransition = Explode()
                exitTransition = Explode()
            }
        }

        super.onCreate(savedInstanceState)

        binding = ActivityAccountSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Check if we're editing an existing account
        val accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L)
        if (accountId != -1L) {
            // Edit mode
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Edit Account"
            viewModel.loadAccountForEdit(accountId)

            // Navigate directly to manual config fragment for editing, clearing back stack
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_setup) as NavHostFragment
            val navController = navHostFragment.navController
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph_setup)
            navGraph.setStartDestination(R.id.manualConfigFragment)
            navController.graph = navGraph

            // Handle back press in edit mode
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            })
        } else {
            // Create mode
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }
    }

    /**
     * Navigate to main app after successful account setup or finish edit
     */
    fun finishSetup() {
        if (viewModel.isEditMode) {
            // Edit mode: Just finish and return to settings
            finish()
        } else {
            // Create mode: Navigate to main app
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (appPreferences.areAnimationsEnabled()) {
                val options = ActivityOptions.makeSceneTransitionAnimation(this)
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // In edit mode, back button should finish the activity and return to settings
        if (viewModel.isEditMode) {
            finish()
            return true
        }

        // In create mode, use normal navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_setup) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
    }
}
