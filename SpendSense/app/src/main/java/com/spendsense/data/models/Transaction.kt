package com.spendsense.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single detected financial deduction (expense) transaction.
 * All data is stored 100% locally in SQLite via Room.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["message_hash"], unique = true), // Prevent duplicates
        Index(value = ["timestamp"]),
        Index(value = ["payment_method"]),
        Index(value = ["bank_name"])
    ]
)
data class Transaction(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Parsed transaction amount in INR */
    @ColumnInfo(name = "amount")
    val amount: Double,

    /** Detected bank name (e.g., "HDFC Bank", "SBI", "ICICI") */
    @ColumnInfo(name = "bank_name")
    val bankName: String = "",

    /** Merchant or payee name */
    @ColumnInfo(name = "merchant")
    val merchant: String = "",

    /** Payment method: UPI, DEBIT_CARD, CREDIT_CARD, NEFT, IMPS, ATM, WALLET, etc. */
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String = "",

    /** Last 4 digits of account/card */
    @ColumnInfo(name = "account_last4")
    val accountLast4: String = "",

    /** Transaction/reference ID from bank */
    @ColumnInfo(name = "transaction_reference")
    val transactionReference: String = "",

    /** Full original SMS or notification text */
    @ColumnInfo(name = "sms_body")
    val smsBody: String,

    /** Source: "SMS" or "NOTIFICATION" */
    @ColumnInfo(name = "source")
    val source: String = "SMS",

    /** Sender/app package that generated the message */
    @ColumnInfo(name = "sender")
    val sender: String = "",

    /** Transaction category (auto-detected) */
    @ColumnInfo(name = "category")
    val category: String = "Other",

    /** Unix timestamp in milliseconds */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /** SHA-256 hash of the original message to detect duplicates */
    @ColumnInfo(name = "message_hash")
    val messageHash: String
)
