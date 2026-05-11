package com.spendsense.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.spendsense.data.db.SpendSenseDatabase
import com.spendsense.data.models.Transaction
import com.spendsense.databinding.ActivityTransactionDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        private val FULL_DATE_FORMAT = SimpleDateFormat("EEEE, dd MMM yyyy 'at' hh:mm:ss a", Locale.getDefault())
    }

    private lateinit var binding: ActivityTransactionDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Transaction Details"

        val id = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        if (id == -1L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val transaction = SpendSenseDatabase.getInstance(applicationContext)
                .transactionDao()
                .getTransactionById(id)

            transaction?.let { bindTransaction(it) } ?: finish()
        }
    }

    private fun bindTransaction(t: Transaction) {
        binding.apply {
            tvDetailAmount.text = "₹%.2f".format(t.amount)
            tvDetailCategory.text = t.category
            tvDetailDate.text = FULL_DATE_FORMAT.format(Date(t.timestamp))

            // Bank
            if (t.bankName.isNotBlank()) {
                tvDetailBank.text = t.bankName
                rowBank.visibility = View.VISIBLE
            } else rowBank.visibility = View.GONE

            // Merchant
            if (t.merchant.isNotBlank()) {
                tvDetailMerchant.text = t.merchant
                rowMerchant.visibility = View.VISIBLE
            } else rowMerchant.visibility = View.GONE

            // Payment method
            tvDetailMethod.text = t.paymentMethod.replace("_", " ")

            // Account
            if (t.accountLast4.isNotBlank()) {
                tvDetailAccount.text = "••••${t.accountLast4}"
                rowAccount.visibility = View.VISIBLE
            } else rowAccount.visibility = View.GONE

            // Reference
            if (t.transactionReference.isNotBlank()) {
                tvDetailRef.text = t.transactionReference
                rowRef.visibility = View.VISIBLE
            } else rowRef.visibility = View.GONE

            // Source
            tvDetailSource.text = when (t.source) {
                "NOTIFICATION" -> "📳 Payment App Notification"
                "SMS_IMPORT" -> "📂 Historical SMS Import"
                else -> "💬 SMS"
            }

            // Sender
            if (t.sender.isNotBlank()) {
                tvDetailSender.text = t.sender
                rowSender.visibility = View.VISIBLE
            } else rowSender.visibility = View.GONE

            // Full SMS body
            tvDetailSmsBody.text = t.smsBody

            // Copy button
            btnCopy.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Transaction", t.smsBody))
                Toast.makeText(this@TransactionDetailActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }

            // Delete button
            btnDelete.setOnClickListener {
                AlertDialog.Builder(this@TransactionDetailActivity)
                    .setTitle("Delete Transaction")
                    .setMessage("Remove this transaction permanently?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            SpendSenseDatabase.getInstance(applicationContext)
                                .transactionDao()
                                .deleteById(t.id)
                            finish()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
