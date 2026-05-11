package com.spendsense.service

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.spendsense.data.db.SpendSenseDatabase
import com.spendsense.detection.TransactionDetectionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens to device notifications from payment apps and banking apps.
 * Processes notification text locally for financial deductions.
 * No internet. No external calls. 100% on-device.
 */
class NotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"
    }

    private val engine = TransactionDetectionEngine
    private lateinit var db: SpendSenseDatabase

    override fun onCreate() {
        super.onCreate()
        db = SpendSenseDatabase.getInstance(applicationContext)
        Log.d(TAG, "NotificationListenerService started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName ?: return

        // Only process notifications from known payment/banking apps
        // and notifications from apps with "bank" or "pay" in their label
        if (!shouldProcessPackage(packageName)) return

        val notification = sbn.notification ?: return
        val extras: Bundle = notification.extras ?: return

        // Extract text content from notification
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // Combine all text for analysis
        val fullText = listOf(title, text, bigText, subText)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .trim()

        if (fullText.isBlank()) return

        Log.d(TAG, "Notification from $packageName: ${fullText.take(100)}")

        val dao = db.transactionDao()

        CoroutineScope(Dispatchers.IO).launch {
            val transaction = engine.analyze(
                messageBody = fullText,
                sender = packageName,
                source = "NOTIFICATION",
                timestamp = sbn.postTime
            ) ?: return@launch

            // Check for duplicate
            val existing = dao.existsByHash(transaction.messageHash)
            if (existing > 0) {
                Log.d(TAG, "Duplicate notification detected, skipping")
                return@launch
            }

            val rowId = dao.insert(transaction)
            if (rowId > 0) {
                Log.i(TAG, "✅ Saved from notification ₹${transaction.amount} via $packageName")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed on removal
    }

    private fun shouldProcessPackage(packageName: String): Boolean {
        // Always process known payment apps
        if (engine.isPaymentApp(packageName)) return true

        // Check app label for banking keywords
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appLabel = packageManager.getApplicationLabel(appInfo).toString().lowercase()
            appLabel.contains("bank") || appLabel.contains("pay") ||
                    appLabel.contains("wallet") || appLabel.contains("finance") ||
                    appLabel.contains("money") || appLabel.contains("upi")
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
