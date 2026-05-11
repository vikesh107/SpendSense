package com.spendsense.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restores background processing after device reboot.
 * NotificationListenerService is system-managed, so we just log and
 * ensure the notification channel is created.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "Device booted — SpendSense ready")
            TransactionNotificationHelper.createNotificationChannel(context)
        }
    }
}
