package com.spendsense.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.spendsense.R
import com.spendsense.data.models.Transaction
import com.spendsense.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val onItemClick: (Transaction) -> Unit,
    private val onItemLongClick: (Transaction) -> Boolean
) : ListAdapter<Transaction, TransactionAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(old: Transaction, new: Transaction) = old.id == new.id
            override fun areContentsTheSame(old: Transaction, new: Transaction) = old == new
        }

        // Category → color mapping
        private val CATEGORY_COLORS = mapOf(
            "Food & Dining" to R.color.cat_food,
            "Shopping" to R.color.cat_shopping,
            "Transport" to R.color.cat_transport,
            "Utilities" to R.color.cat_utilities,
            "Mobile & Internet" to R.color.cat_mobile,
            "Entertainment" to R.color.cat_entertainment,
            "Health" to R.color.cat_health,
            "ATM Withdrawal" to R.color.cat_atm,
            "EMI" to R.color.cat_emi,
            "Grocery" to R.color.cat_grocery,
            "Travel" to R.color.cat_travel,
            "Education" to R.color.cat_education
        )

        // Category → emoji mapping
        private val CATEGORY_ICONS = mapOf(
            "Food & Dining" to "🍽️",
            "Shopping" to "🛒",
            "Transport" to "🚗",
            "Utilities" to "💡",
            "Mobile & Internet" to "📱",
            "Entertainment" to "🎬",
            "Health" to "💊",
            "ATM Withdrawal" to "🏧",
            "EMI" to "📋",
            "Grocery" to "🥦",
            "Travel" to "✈️",
            "Education" to "📚",
            "Insurance" to "🛡️",
            "Investment" to "📈",
            "Rent" to "🏠",
            "Subscription" to "🔄"
        )
    }

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(transaction: Transaction) {
            val ctx = binding.root.context

            // Amount
            binding.tvAmount.text = "₹%.2f".format(transaction.amount)

            // Category icon + name
            val icon = CATEGORY_ICONS[transaction.category] ?: "💸"
            binding.tvCategory.text = "$icon ${transaction.category}"

            // Bank or method
            val source = when {
                transaction.bankName.isNotBlank() -> transaction.bankName
                transaction.paymentMethod.isNotBlank() -> transaction.paymentMethod
                else -> transaction.sender.takeLast(20)
            }
            binding.tvBank.text = source

            // Merchant
            val merchant = when {
                transaction.merchant.isNotBlank() -> transaction.merchant
                else -> transaction.paymentMethod.ifBlank { "Unknown" }
            }
            binding.tvMerchant.text = merchant

            // Date
            binding.tvDate.text = DATE_FORMAT.format(Date(transaction.timestamp))

            // Payment method badge
            binding.tvMethod.text = transaction.paymentMethod.replace("_", " ")

            // Category color indicator
            val colorRes = CATEGORY_COLORS[transaction.category] ?: R.color.cat_other
            binding.viewCategoryBar.setBackgroundColor(
                ContextCompat.getColor(ctx, colorRes)
            )

            // Source badge (SMS vs Notification)
            binding.tvSource.text = when (transaction.source) {
                "NOTIFICATION" -> "🔔"
                "SMS_IMPORT" -> "📂"
                else -> "💬"
            }

            binding.root.setOnClickListener { onItemClick(transaction) }
            binding.root.setOnLongClickListener { onItemLongClick(transaction) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
