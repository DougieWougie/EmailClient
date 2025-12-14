package com.emailclient.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.transition.Explode
import android.view.Menu
import android.view.MenuItem
import android.view.Window
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val viewModel: MainViewModel by viewModels()

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
        setupFab()
        checkForAccounts()
    }

    private fun setupFab() {
        binding.fabCompose.setOnClickListener {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController
            navController.navigate(R.id.composeFragment)
        }
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

        menu.add(Menu.NONE, R.id.nav_settings, 101, R.string.nav_settings)
            .setIcon(android.R.drawable.ic_menu_manage)

        menu.add(Menu.NONE, R.id.nav_folder_management, 102, R.string.nav_folder_management)
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
        headerView.setOnClickListener {
            showAccountSwitcher()
        }
    }

    private fun updateDrawerHeader(account: Account?) {
        val headerView = binding.navView.getHeaderView(0)
        val displayNameView = headerView.findViewById<TextView>(R.id.text_account_name)
        val emailView = headerView.findViewById<TextView>(R.id.text_account_email)
        val avatarView = headerView.findViewById<ImageView>(R.id.image_account_avatar)

        displayNameView.text = account?.displayName ?: getString(R.string.no_account)
        emailView.text = account?.email ?: ""

        // Load profile image
        if (account?.profileImageUri != null) {
            avatarView.load(Uri.parse(account.profileImageUri)) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person)
                error(R.drawable.ic_person)
            }
        } else {
            avatarView.setImageResource(R.drawable.ic_person)
        }
    }

    private fun showAccountSwitcher() {
        val accounts = viewModel.allAccounts.value
        if (accounts.size <= 1) {
            // Only one account, nothing to switch
            return
        }

        val accountDisplays = accounts.map { "${it.displayName}\n${it.email}" }.toTypedArray()
        val currentAccountId = viewModel.currentAccount.value?.id
        val selectedIndex = accounts.indexOfFirst { it.id == currentAccountId }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.switch_account)
            .setSingleChoiceItems(accountDisplays, selectedIndex) { dialog, which ->
                val selectedAccount = accounts[which]
                viewModel.switchAccount(selectedAccount.id)

                // Navigate to inbox of new account
                findNavController(R.id.nav_host_fragment).navigate(R.id.inboxFragment)

                dialog.dismiss()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
