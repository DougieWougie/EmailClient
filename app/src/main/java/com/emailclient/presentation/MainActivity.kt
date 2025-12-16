package com.emailclient.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.transition.Explode
import android.transition.Fade
import android.transition.TransitionManager
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.Window
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.emailclient.R
import com.emailclient.data.local.AppPreferences
import com.emailclient.databinding.ActivityMainBinding
import com.emailclient.domain.model.Account
import com.emailclient.domain.model.Folder
import com.emailclient.domain.model.FolderType
import com.emailclient.presentation.setup.AccountSetupActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val viewModel: MainViewModel by viewModels()
    private var chevronIcon: ImageView? = null
    private var previousAccountId: Long? = null

    @Inject
    lateinit var appPreferences: AppPreferences

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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Configure top-level destinations and drawer
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.inboxFragment, R.id.folderViewFragment),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Setup NavigationView item selection
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            handleDrawerItemClick(menuItem)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        setupDrawer()
        setupDrawerHeader()
        checkForAccounts()
    }

    private fun checkForAccounts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasAccounts.collect { hasAccounts ->
                    if (!hasAccounts) {
                        // No accounts, launch setup
                        val intent = Intent(this@MainActivity, AccountSetupActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupDrawer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe folders and update menu
                launch {
                    viewModel.folders.collect { folders ->
                        updateDrawerMenu(folders)
                    }
                }

                // Observe current account and update header
                launch {
                    viewModel.currentAccount.collect { account ->
                        updateDrawerHeader(account)
                    }
                }
            }
        }
    }

    private fun updateDrawerMenu(folders: List<Folder>) {
        val menu = binding.navView.menu
        menu.clear()

        if (folders.isEmpty()) {
            menu.add("No folders available").isEnabled = false
            return
        }

        // Add folders (system folders first, then custom)
        val systemFolders = folders.filter { it.type != FolderType.CUSTOM }
            .sortedBy { it.type.ordinal }
        val customFolders = folders.filter { it.type == FolderType.CUSTOM }
            .sortedBy { it.displayName }

        // Create a folders group
        val foldersGroup = menu.addSubMenu("Folders")

        (systemFolders + customFolders).forEachIndexed { index, folder ->
            val title = if (folder.unreadCount > 0) {
                "${folder.displayName} (${folder.unreadCount})"
            } else {
                folder.displayName
            }

            val menuItem = foldersGroup.add(
                R.id.group_folders,
                folder.id.toInt(),
                index,
                title
            )
            menuItem.setIcon(getFolderIcon(folder.type))
            menuItem.isCheckable = true
        }

        // Add divider and static items
        menu.add(Menu.NONE, Menu.NONE, 100, "").apply {
            isEnabled = false
        }

        // Add Switch Account menu item (only if multiple accounts)
        if (viewModel.allAccounts.value.size > 1) {
            menu.add(Menu.NONE, R.id.nav_switch_account, 101, R.string.nav_switch_account)
                .setIcon(R.drawable.ic_person)
        }

        menu.add(Menu.NONE, R.id.nav_settings, 102, R.string.nav_settings)
            .setIcon(android.R.drawable.ic_menu_manage)

        menu.add(Menu.NONE, R.id.nav_folder_management, 103, R.string.nav_folder_management)
            .setIcon(R.drawable.ic_folder)
    }

    private fun getFolderIcon(type: FolderType): Int {
        return when (type) {
            FolderType.INBOX -> R.drawable.ic_inbox
            FolderType.SENT -> R.drawable.ic_send
            FolderType.DRAFTS -> R.drawable.ic_drafts
            FolderType.TRASH -> R.drawable.ic_delete
            FolderType.SPAM -> R.drawable.ic_spam
            FolderType.ARCHIVE -> R.drawable.ic_archive
            FolderType.CUSTOM -> R.drawable.ic_folder
        }
    }

    private fun handleDrawerItemClick(menuItem: MenuItem) {
        val navController = findNavController(R.id.nav_host_fragment)

        when (menuItem.itemId) {
            R.id.nav_switch_account -> {
                showAccountSwitcher()
                return // Don't close drawer, dialog will handle it
            }
            R.id.nav_settings -> {
                navController.navigate(R.id.settingsFragment)
            }
            R.id.nav_folder_management -> {
                navController.navigate(R.id.folderManagementFragment)
            }
            else -> {
                // It's a folder - menuItem.itemId is the folderId
                val folderId = menuItem.itemId.toLong()
                val folder = viewModel.folders.value.find { it.id == folderId }

                folder?.let {
                    val bundle = Bundle().apply {
                        putLong("folderId", it.id)
                        putString("folderName", it.displayName)
                    }
                    navController.navigate(R.id.action_global_folder_view, bundle)
                }
            }
        }
    }

    private fun setupDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)
        chevronIcon = headerView.findViewById(R.id.icon_expand_chevron)

        headerView.setOnClickListener {
            animateChevronRotation(expand = true)
            showAccountSwitcher()
        }

        // Observe account count to show/hide chevron
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allAccounts.collect { accounts ->
                    chevronIcon?.visibility = if (accounts.size > 1) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                }
            }
        }
    }

    private fun animateChevronRotation(expand: Boolean) {
        if (!appPreferences.areAnimationsEnabled()) return

        chevronIcon?.animate()
            ?.rotation(if (expand) 180f else 0f)
            ?.setDuration(200)
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.start()
    }

    private fun updateDrawerHeader(account: Account?) {
        val headerView = binding.navView.getHeaderView(0)
        val displayNameView = headerView.findViewById<TextView>(R.id.text_account_name)
        val emailView = headerView.findViewById<TextView>(R.id.text_account_email)
        val avatarView = headerView.findViewById<ImageView>(R.id.image_account_avatar)

        val animationsEnabled = appPreferences.areAnimationsEnabled()
        val isAccountSwitch = previousAccountId != null && previousAccountId != account?.id

        if (animationsEnabled && isAccountSwitch) {
            // Animate text changes
            displayNameView.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    displayNameView.text = account?.displayName ?: getString(R.string.no_account)
                    displayNameView.animate().alpha(1f).setDuration(150).start()
                }
                .start()

            emailView.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    emailView.text = account?.email ?: ""
                    emailView.animate().alpha(1f).setDuration(150).start()
                }
                .start()

            // Add subtle scale animation to avatar
            avatarView.scaleX = 0.95f
            avatarView.scaleY = 0.95f
            avatarView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            // No animation - instant update
            displayNameView.text = account?.displayName ?: getString(R.string.no_account)
            emailView.text = account?.email ?: ""
        }

        // Load profile image with animation
        if (account?.profileImageUri != null && account.profileImageUri.isNotEmpty()) {
            avatarView.load(Uri.parse(account.profileImageUri)) {
                if (animationsEnabled && isAccountSwitch) {
                    crossfade(500)
                } else {
                    crossfade(true)
                }
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person)
                error(R.drawable.ic_person)
            }
        } else {
            // No profile image - set default icon
            if (animationsEnabled && isAccountSwitch) {
                avatarView.load(R.drawable.ic_person) {
                    crossfade(500)
                    transformations(CircleCropTransformation())
                }
            } else {
                avatarView.setImageResource(R.drawable.ic_person)
            }
        }

        // Update previous account ID for next comparison
        previousAccountId = account?.id
    }

    private fun showAccountSwitcher() {
        val accounts = viewModel.allAccounts.value
        if (accounts.size <= 1) {
            // Only one account, nothing to switch
            animateChevronRotation(expand = false)
            Snackbar.make(
                binding.root,
                "Add another account in Settings to switch between accounts",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // Add haptic feedback if animations enabled
        if (appPreferences.areAnimationsEnabled()) {
            binding.navView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        val accountDisplays = accounts.map { "${it.displayName}\n${it.email}" }.toTypedArray()
        val currentAccountId = viewModel.currentAccount.value?.id
        val selectedIndex = accounts.indexOfFirst { it.id == currentAccountId }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.switch_account)
            .setSingleChoiceItems(accountDisplays, selectedIndex) { dialog, which ->
                val selectedAccount = accounts[which]
                viewModel.switchAccount(selectedAccount.id)

                // Animate drawer content refresh
                if (appPreferences.areAnimationsEnabled()) {
                    TransitionManager.beginDelayedTransition(
                        binding.navView,
                        Fade().apply { duration = 200 }
                    )
                }

                // Navigate to inbox with slight delay for animation
                lifecycleScope.launch {
                    if (appPreferences.areAnimationsEnabled()) {
                        delay(200)
                    }
                    findNavController(R.id.nav_host_fragment).navigate(R.id.inboxFragment)

                    // Show confirmation
                    delay(300)
                    showAccountSwitchConfirmation(selectedAccount)
                }

                dialog.dismiss()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            .setNegativeButton("Cancel") { _, _ ->
                animateChevronRotation(expand = false)
            }
            .setOnDismissListener {
                animateChevronRotation(expand = false)
            }
            .show()
    }

    private fun showAccountSwitchConfirmation(account: Account) {
        Snackbar.make(
            binding.root,
            getString(R.string.switched_to_account, account.displayName),
            Snackbar.LENGTH_SHORT
        ).show()
    }
}
