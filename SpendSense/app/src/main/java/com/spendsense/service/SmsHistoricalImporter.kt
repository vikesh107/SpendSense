package com.spendsense.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.spendsense.data.db.SpendSenseDatabase
import com.spendsense.detection.TransactionDetectionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the device's SMS inbox for historical financial transactions.
 * Called once on first launch or manually via Settings.
 * 100% local — reads from device content provider only.
 */
object SmsHistoricalImporter {

    private const val TAG = "SmsImporter"
    private const val SMS_URI = "content://sms/inbox"

    data class ImportResult(
        val total: Int,
        val imported: Int,
        val duplicates: Int
    )

    suspend fun importHistoricalSms(context: Context): ImportResult = withContext(Dispatchers.IO) {
        val db = SpendSenseDatabase.getInstance(context)
        val dao = db.transactionDao()
        val engine = TransactionDetectionEngine

        var total = 0
        var imported = 0
        var duplicates = 0

        try {
            val uri = Uri.parse(SMS_URI)
            val projection = arrayOf("_id", "address", "body", "date", "type")

            // type = 1 means received SMS
            val cursor = context.contentResolver.query(
                uri, projection,
                "type = 1",  // inbox only
                null,
                "date DESC"
            )

            cursor?.use { c ->
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val addressIdx = c.getColumnIndexOrThrow("address")
                val dateIdx = c.getColumnIndexOrThrow("date")

                Log.d(TAG, "Total SMS in inbox: ${c.count}")

                while (c.moveToNext()) {
                    val body = c.getString(bodyIdx) ?: continue
                    val sender = c.getString(addressIdx) ?: ""
                    val date = c.getLong(dateIdx)

                    total++

                    // Quick pre-filter on sender to reduce processing
                    val transaction = engine.analyze(
                        messageBody = body,
                        sender = sender,
                        source = "SMS_IMPORT",
                        timestamp = date
                    ) ?: continue

                    // Check duplicate
                    val existingCount = dao.existsByHash(transaction.messageHash)
                    if (existingCount > 0) {
                        duplicates++
                        continue
                    }

                    val rowId = dao.insert(transaction)
                    if (rowId > 0) {
                        imported++
                        Log.d(TAG, "Imported: ₹${transaction.amount} from ${transaction.bankName} on ${java.util.Date(date)}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing historical SMS", e)
        }

        Log.i(TAG, "Import complete: $total total, $imported imported, $duplicates duplicates")
        ImportResult(total, imported, duplicates)
    }
}
