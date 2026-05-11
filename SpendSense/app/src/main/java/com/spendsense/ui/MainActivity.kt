package com.spendsense.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.spendsense.R
import com.spendsense.data.models.Transaction
import com.spendsense.databinding.ActivityMainBinding
import com.spendsense.service.TransactionNotificationHelper
import com.spendsense.ui.adapters.TransactionAdapter
import com.spendsense.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: TransactionAdapter

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.READ_SMS] == true
        if (smsGranted) {
            showImportPrompt()
        } else {
            Snackbar.make(binding.root, "SMS permission needed to detect transactions", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        TransactionNotificationHelper.createNotificationChannel(this)

        setupRecyclerView()
        setupSearch()
        setupObservers()
        checkPermissions()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(
            onItemClick = { transaction -> openDetail(transaction) },
            onItemLongClick = { transaction -> showDeleteDialog(transaction); true }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString())
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.clearFilters()
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.transactions.collectLatest { transactions ->
                adapter.submitList(transactions)
                binding.tvEmpty.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (transactions.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        lifecycleScope.launch {
            viewModel.totalSpent.collectLatest { total ->
                binding.tvTotalSpent.text = "₹%.2f".format(total)
            }
        }

        lifecycleScope.launch {
            viewModel.thisMonthTotal.collectLatest { total ->
                binding.tvMonthTotal.text = "₹%.2f".format(total)
            }
        }

        lifecycleScope.launch {
            viewModel.todayTotal.collectLatest { total ->
                binding.tvTodayTotal.text = "₹%.2f".format(total)
            }
        }

        lifecycleScope.launch {
            viewModel.transactionCount.collectLatest { count ->
                binding.tvCount.text = "$count transactions"
            }
        }

        viewModel.importStatus.observe(this) { status ->
            when (status) {
                is MainViewModel.ImportStatus.Running -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvImportStatus.text = "Scanning SMS inbox…"
                    binding.tvImportStatus.visibility = View.VISIBLE
                }
                is MainViewModel.ImportStatus.Done -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvImportStatus.text = "✅ Found ${status.imported} transactions from ${status.total} SMS"
                    Snackbar.make(binding.root,
                        "Imported ${status.imported} transactions", Snackbar.LENGTH_LONG).show()
                }
                is MainViewModel.ImportStatus.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvImportStatus.visibility = View.GONE
                    Snackbar.make(binding.root, "Error: ${status.message}", Snackbar.LENGTH_LONG).show()
                }
                null -> {}
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun checkPermissions() {
        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        val hasReceiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasSms || !hasReceiveSms) {
            showPermissionExplanation()
        } else {
            checkNotificationListenerPermission()
        }
    }

    private fun showPermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("📱 SMS Permission Required")
            .setMessage(
                "SpendSense needs READ_SMS permission to automatically detect your bank transactions.\n\n" +
                        "• All processing is 100% local on your device\n" +
                        "• No data is ever sent to any server\n" +
                        "• Works completely offline"
            )
            .setPositiveButton("Grant Permission") { _, _ ->
                requestSmsPermission.launch(
                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                )
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun checkNotificationListenerPermission() {
        val cn = ComponentName(this, com.spendsense.service.NotificationListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val enabled = flat?.contains(cn.flattenToString()) == true

        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle("🔔 Enable Notification Access")
                .setMessage(
                    "Allow SpendSense to read payment app notifications (Google Pay, PhonePe, Paytm, banking apps) for automatic transaction detection.\n\n" +
                            "This is optional — SMS detection still works without it."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }

    private fun showImportPrompt() {
        checkNotificationListenerPermission()
        AlertDialog.Builder(this)
            .setTitle("📂 Import Past Transactions?")
            .setMessage("Scan your SMS inbox to import historical bank transactions. This runs only on your device.")
            .setPositiveButton("Import Now") { _, _ -> viewModel.startSmsImport() }
            .setNegativeButton("Not Now", null)
            .show()
    }

    private fun openDetail(transaction: Transaction) {
        val intent = Intent(this, TransactionDetailActivity::class.java).apply {
            putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, transaction.id)
        }
        startActivity(intent)
    }

    private fun showDeleteDialog(transaction: Transaction) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Remove ₹${"%.2f".format(transaction.amount)} from ${transaction.bankName}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTransaction(transaction)
                Snackbar.make(binding.root, "Transaction deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED) {
                    viewModel.startSmsImport()
                } else {
                    showPermissionExplanation()
                }
                true
            }
            R.id.action_clear_all -> {
                AlertDialog.Builder(this)
                    .setTitle("Clear All Transactions")
                    .setMessage("This will permanently delete all tracked transactions. This cannot be undone.")
                    .setPositiveButton("Clear All") { _, _ ->
                        lifecycleScope.launch {
                            com.spendsense.data.db.SpendSenseDatabase
                                .getInstance(applicationContext)
                                .transactionDao()
                                .deleteAll()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("SpendSense")
            .setMessage(
                "Version 1.0\n\n" +
                        "Fully offline automatic expense tracker.\n\n" +
                        "🔒 Privacy Guarantee:\n" +
                        "• No internet access\n" +
                        "• No cloud sync\n" +
                        "• All data stays on your device\n" +
                        "• No account or login required\n\n" +
                        "Detects transactions from SMS and payment app notifications automatically."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
