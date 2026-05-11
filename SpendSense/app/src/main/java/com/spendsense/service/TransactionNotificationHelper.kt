package com.spendsense.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.spendsense.R
import com.spendsense.data.models.Transaction
import com.spendsense.ui.MainActivity

object TransactionNotificationHelper {

    private const val CHANNEL_ID = "spendsense_alerts"
    private const val CHANNEL_NAME = "New Transaction Alerts"
    private var notifId = 1000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new expense is detected"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showNewTransactionAlert(context: Context, transaction: Transaction) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = "₹%.2f".format(transaction.amount)
        val merchant = if (transaction.merchant.isNotBlank()) " @ ${transaction.merchant}" else ""
        val bank = if (transaction.bankName.isNotBlank()) " via ${transaction.bankName}" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("💸 Expense Detected: $amountStr")
            .setContentText("${transaction.paymentMethod}$merchant$bank")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(transaction.smsBody.take(200)))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId++, notification)
    }
}
