package com.spendsense.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.spendsense.data.db.SpendSenseDatabase
import com.spendsense.detection.TransactionDetectionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming SMS messages and processes them for financial transactions.
 * 100% local — no network access.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group multi-part SMS by sender
        val grouped = mutableMapOf<String, StringBuilder>()
        val senders = mutableMapOf<String, String>()
        val timestamps = mutableMapOf<String, Long>()

        for (msg in messages) {
            val sender = msg.originatingAddress ?: "UNKNOWN"
            val key = sender
            grouped.getOrPut(key) { StringBuilder() }.append(msg.messageBody)
            senders[key] = sender
            timestamps[key] = msg.timestampMillis
        }

        val db = SpendSenseDatabase.getInstance(context)
        val dao = db.transactionDao()
        val engine = TransactionDetectionEngine

        CoroutineScope(Dispatchers.IO).launch {
            for ((key, bodyBuilder) in grouped) {
                val body = bodyBuilder.toString()
                val sender = senders[key] ?: ""
                val ts = timestamps[key] ?: System.currentTimeMillis()

                Log.d(TAG, "Processing SMS from $sender: ${body.take(80)}")

                // Pre-filter: only process messages from likely financial senders
                // This reduces noise but also process ALL if sender unknown
                val transaction = engine.analyze(
                    messageBody = body,
                    sender = sender,
                    source = "SMS",
                    timestamp = ts
                ) ?: continue

                // Check for duplicate before inserting
                val existing = dao.existsByHash(transaction.messageHash)
                if (existing > 0) {
                    Log.d(TAG, "Duplicate SMS detected, skipping")
                    continue
                }

                val rowId = dao.insert(transaction)
                if (rowId > 0) {
                    Log.i(TAG, "✅ Saved transaction ₹${transaction.amount} from ${transaction.bankName}")
                    // Optionally post a local notification
                    TransactionNotificationHelper.showNewTransactionAlert(context, transaction)
                }
            }
        }
    }
}
