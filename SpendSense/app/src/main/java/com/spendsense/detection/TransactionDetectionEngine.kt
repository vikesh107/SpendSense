package com.spendsense.detection

import android.util.Log
import com.spendsense.data.models.Transaction
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * TransactionDetectionEngine
 *
 * Fully on-device, zero-network transaction parser.
 * Uses regex + keyword pattern matching to detect real financial deductions
 * from SMS/notification text.
 *
 * Supports: Bank debits, UPI, debit/credit cards, ATM, FASTag, bills,
 * recharges, subscriptions, EMIs, wallets, shopping, auto-debit.
 */
object TransactionDetectionEngine {

    private const val TAG = "DetectionEngine"

    // ─── DEDUCTION KEYWORDS ───────────────────────────────────────────────────
    private val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "spent", "paid", "payment", "purchase",
        "withdrawn", "withdrawal", "auto-debit", "auto debit", "auto_debit",
        "sent", "transferred", "transfer", "deducted", "deduction",
        "charged", "bill paid", "recharge", "recharged",
        "upi txn", "upi transaction", "txn successful", "transaction successful",
        "payment successful", "payment done", "payment confirmed",
        "money sent", "amount debited", "amount deducted",
        "emi deducted", "emi debited", "subscription charged",
        "fastag", "toll paid", "toll deducted",
        "mandate executed", "nach debit", "standing instruction",
        "atm withdrawal", "cash withdrawal",
        "order placed", "order confirmed", "shopping"
    )

    // ─── EXCLUSION KEYWORDS (not real deductions) ─────────────────────────────
    private val EXCLUDE_KEYWORDS = listOf(
        "credited", "credit", "received", "added", "cashback",
        "refund", "reversed", "reversal", "reward", "bonus",
        "otp", "one time password", "verification code",
        "offer", "discount", "off on", "exclusive deal",
        "congratulations", "you have won", "prize",
        "loan approved", "pre-approved", "limit increased",
        "statement", "available balance", "account balance",
        "minimum due", "total due", "outstanding",
        "linked", "registered", "activated", "updated",
        "login", "signed in", "password", "pin changed",
        "kyc", "nomination", "nominee"
    )

    // ─── AMOUNT PATTERNS ──────────────────────────────────────────────────────
    private val AMOUNT_PATTERNS = listOf(
        // Rs. 1,234.56 or Rs 1234.56
        Regex("""(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // INR1,234.56
        Regex("""INR\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // Amount: 1234.56 or Amount of 1234.56
        Regex("""amount(?:\s+of)?\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // debited/spent/paid + amount
        Regex("""(?:debited|deducted|withdrawn|spent|paid|sent)\s+(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // ₹1234
        Regex("""₹\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
        // Standalone number after currency keyword
        Regex("""(?:for|of|worth)\s+(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // UPI: number
        Regex("""(?:upi|imps|neft|rtgs|transfer)\s+(?:of\s+)?(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    // ─── BANK DETECTION ───────────────────────────────────────────────────────
    private val BANK_PATTERNS = mapOf(
        "HDFC Bank" to listOf("hdfc", "hdfcbank"),
        "SBI" to listOf("sbi", "state bank of india", "statebank"),
        "ICICI Bank" to listOf("icici", "icicib"),
        "Axis Bank" to listOf("axis", "axisbank"),
        "Kotak Bank" to listOf("kotak", "kotakbank"),
        "Punjab National Bank" to listOf("pnb", "punjab national"),
        "Bank of Baroda" to listOf("bob", "bankofbaroda"),
        "Canara Bank" to listOf("canara", "canarabank"),
        "Union Bank" to listOf("unionbank", "union bank"),
        "Yes Bank" to listOf("yesbank", "yes bank"),
        "IndusInd Bank" to listOf("indusind", "indusindbank"),
        "Federal Bank" to listOf("federal bank", "federalbank"),
        "IDBI Bank" to listOf("idbi"),
        "Indian Bank" to listOf("indianbank", "indian bank"),
        "Bank of India" to listOf("boi", "bank of india"),
        "Central Bank" to listOf("central bank", "centralbank"),
        "UCO Bank" to listOf("uco bank", "ucobank"),
        "IOB" to listOf("iob", "indian overseas"),
        "RBL Bank" to listOf("rbl bank", "rblbank"),
        "DCB Bank" to listOf("dcb bank"),
        "Bandhan Bank" to listOf("bandhan"),
        "AU Small Finance" to listOf("au small", "aubank"),
        "IDFC First" to listOf("idfc"),
        "Paytm Payments Bank" to listOf("paytm bank", "paytmbank"),
        "Airtel Payments Bank" to listOf("airtel bank", "airtelbank"),
        "Amazon Pay" to listOf("amazon pay", "amazonpay"),
        "PhonePe" to listOf("phonepe", "phone pe"),
        "Google Pay" to listOf("google pay", "gpay", "googlepay"),
        "Paytm" to listOf("paytm"),
        "Mobikwik" to listOf("mobikwik"),
        "Freecharge" to listOf("freecharge"),
        "Ola Money" to listOf("ola money"),
        "Juspay" to listOf("juspay")
    )

    // ─── PAYMENT METHOD PATTERNS ──────────────────────────────────────────────
    private val PAYMENT_METHOD_PATTERNS = mapOf(
        "UPI" to listOf("upi", "bhim", "vpa", "@okaxis", "@oksbi", "@okicici", "@okhdfc", "@ybl", "@ibl", "@axl"),
        "CREDIT_CARD" to listOf("credit card", "creditcard", "cc ", "c.c."),
        "DEBIT_CARD" to listOf("debit card", "debitcard", "dc ", "d.c."),
        "NEFT" to listOf("neft"),
        "IMPS" to listOf("imps"),
        "RTGS" to listOf("rtgs"),
        "ATM" to listOf("atm", "cash withdrawal", "atm withdrawal"),
        "WALLET" to listOf("wallet", "paytm wallet", "mobikwik", "freecharge"),
        "FASTAG" to listOf("fastag", "toll", "netc"),
        "EMI" to listOf("emi", "equated monthly"),
        "NACH/MANDATE" to listOf("nach", "mandate", "auto-debit", "standing instruction"),
        "NET_BANKING" to listOf("net banking", "netbanking", "internet banking"),
        "SUBSCRIPTION" to listOf("subscription", "recurring", "auto-renewal", "renewal charge")
    )

    // ─── CATEGORY DETECTION ───────────────────────────────────────────────────
    private val CATEGORY_PATTERNS = mapOf(
        "Food & Dining" to listOf("swiggy", "zomato", "restaurant", "food", "dining", "cafe", "pizza", "burger", "hotel", "dhaba"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "snapdeal", "shopping", "mall", "store"),
        "Transport" to listOf("uber", "ola", "rapido", "metro", "bus", "cab", "taxi", "fuel", "petrol", "diesel", "fastag", "toll"),
        "Utilities" to listOf("electricity", "water bill", "gas bill", "bses", "tata power", "reliance energy", "adani", "igl", "mahanagar gas"),
        "Mobile & Internet" to listOf("airtel", "jio", "vi ", "vodafone", "idea", "bsnl", "broadband", "recharge", "mobile"),
        "Entertainment" to listOf("netflix", "amazon prime", "hotstar", "zee5", "sonyliv", "spotify", "gaana", "movie", "cinema", "pvr", "inox"),
        "Health" to listOf("apollo", "medplus", "netmeds", "1mg", "pharmacy", "medical", "doctor", "hospital", "clinic", "medicine"),
        "Education" to listOf("byjus", "unacademy", "coursera", "udemy", "school", "college", "education", "fees", "tuition"),
        "ATM Withdrawal" to listOf("atm", "cash withdrawal"),
        "EMI" to listOf("emi", "loan emi", "equated"),
        "Insurance" to listOf("insurance", "lic", "hdfc life", "icici lombard", "bajaj allianz", "premium"),
        "Investment" to listOf("mutual fund", "sip", "demat", "stocks", "nps"),
        "Rent" to listOf("rent", "pg ", "hostel", "housing"),
        "Subscription" to listOf("subscription", "renewal", "plan"),
        "Travel" to listOf("irctc", "flight", "makemytrip", "goibibo", "yatra", "oyo", "hotel booking"),
        "Grocery" to listOf("bigbasket", "grofers", "blinkit", "instamart", "dunzo", "grocery", "supermarket", "dmart", "reliance fresh")
    )

    // ─── MERCHANT EXTRACTION PATTERNS ────────────────────────────────────────
    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?:at|to|for|towards|merchant)\s+([A-Z][A-Za-z0-9\s&\-\.]+?)(?:\s+(?:on|via|using|with|ref|txn|vpa|upi|id|#)|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:paid to|sent to|transferred to)\s+([A-Za-z0-9\s&\-\.@]+?)(?:\s+(?:on|via|ref|txn|upi|id|#)|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""VPA\s+([A-Za-z0-9@\.\-_]+)""", RegexOption.IGNORE_CASE),
        Regex("""UPI(?:\s+ID)?:?\s+([A-Za-z0-9@\.\-_]+)""", RegexOption.IGNORE_CASE)
    )

    // ─── ACCOUNT NUMBER PATTERNS ──────────────────────────────────────────────
    private val ACCOUNT_PATTERNS = listOf(
        Regex("""(?:a/c|ac|account|acct)(?:\s+no\.?|number)?\s*[:\-]?\s*[xX*]+([0-9]{4})""", RegexOption.IGNORE_CASE),
        Regex("""(?:card|c/c|cc)\s*[xX*]+([0-9]{4})""", RegexOption.IGNORE_CASE),
        Regex("""[xX*]{4,}\s*([0-9]{4})"""),
        Regex("""ending\s+(?:with\s+)?([0-9]{4})""", RegexOption.IGNORE_CASE),
        Regex("""last\s+4\s+digits?\s+([0-9]{4})""", RegexOption.IGNORE_CASE)
    )

    // ─── REFERENCE ID PATTERNS ────────────────────────────────────────────────
    private val REFERENCE_PATTERNS = listOf(
        Regex("""(?:ref(?:erence)?(?:\s+no\.?|#|id)?|txn\s*(?:id|no|#)?|transaction\s*(?:id|no|#)?|rrn|utr)\s*[:\-]?\s*([A-Z0-9]{8,25})""", RegexOption.IGNORE_CASE),
        Regex("""(?:imps|neft|upi)\s+(?:ref(?:no)?|id|no)\s*[:\-]?\s*([0-9]{8,20})""", RegexOption.IGNORE_CASE),
        Regex("""order\s+(?:id|no|#)\s*[:\-]?\s*([A-Z0-9\-]{6,25})""", RegexOption.IGNORE_CASE)
    )

    // ─── SENDER/SOURCE WHITELISTS ─────────────────────────────────────────────
    private val FINANCIAL_SENDERS = setOf(
        // Bank SMS senders (typical 6-char alphanumeric)
        "hdfc", "sbi", "icici", "axis", "kotak", "pnb", "bob", "canara",
        "union", "yesbnk", "indus", "federal", "idbi", "iob", "boi",
        "rbl", "dcb", "bandhan", "idfc", "au",
        // UPI/Wallet apps
        "paytm", "phonepe", "gpay", "bhim", "amazonpay", "mobikwik",
        // Generic financial keywords in sender
        "bank", "finance", "wallet", "pay", "cash"
    )

    // ─── UPI APP PACKAGES ─────────────────────────────────────────────────────
    val UPI_APP_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",   // Google Pay
        "net.one97.paytm",                           // Paytm
        "com.phonepe.app",                           // PhonePe
        "in.org.npci.upiapp",                        // BHIM
        "com.amazon.mShop.android.shopping",         // Amazon Pay
        "com.mobikwik_new",                          // MobiKwik
        "com.freecharge.android",                    // FreeCharge
        "com.axis.mobile",                           // Axis Mobile
        "com.csam.icici.bank.imobile",               // ICICI iMobile
        "com.snapwork.hdfc",                         // HDFC MobileBanking
        "com.sbi.SBIFreedomPlus",                    // SBI YONO
        "com.yono.sbi",                              // SBI YONO alt
        "com.kotak.mobile.android",                  // Kotak Mobile
        "com.pnb.mbanking",                          // PNB Mobile
        "com.citi.mobile",                           // Citibank
        "com.indusind.mobile",                       // IndusInd
        "com.yes.mobile",                            // Yes Mobile
        "com.whatsapp",                              // WhatsApp Pay
        "com.paytmmall"                              // Paytm Mall
    )

    // ═════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Analyze a raw SMS/notification body and determine if it represents
     * a real financial deduction. Returns a [Transaction] if valid, null otherwise.
     */
    fun analyze(
        messageBody: String,
        sender: String = "",
        source: String = "SMS",
        timestamp: Long = System.currentTimeMillis()
    ): Transaction? {
        val normalized = messageBody.trim()
        if (normalized.isBlank()) return null

        // Step 1: Check if message contains deduction keywords
        if (!isFinancialDeduction(normalized)) {
            Log.d(TAG, "Rejected (no deduction keyword): ${normalized.take(60)}")
            return null
        }

        // Step 2: Check exclusion keywords
        if (shouldExclude(normalized)) {
            Log.d(TAG, "Excluded (exclusion keyword): ${normalized.take(60)}")
            return null
        }

        // Step 3: Extract amount
        val amount = extractAmount(normalized) ?: run {
            Log.d(TAG, "Rejected (no amount found): ${normalized.take(60)}")
            return null
        }

        // Sanity check: ignore amounts < ₹1 or > ₹10 crore
        if (amount < 1.0 || amount > 100_000_000.0) {
            Log.d(TAG, "Rejected (amount out of range $amount): ${normalized.take(60)}")
            return null
        }

        // Step 4: Extract all other fields
        val bank = extractBank(normalized, sender)
        val merchant = extractMerchant(normalized)
        val paymentMethod = extractPaymentMethod(normalized)
        val accountLast4 = extractAccountLast4(normalized)
        val reference = extractReference(normalized)
        val category = detectCategory(normalized, merchant)
        val hash = computeHash(normalized)

        return Transaction(
            amount = amount,
            bankName = bank,
            merchant = merchant,
            paymentMethod = paymentMethod,
            accountLast4 = accountLast4,
            transactionReference = reference,
            smsBody = normalized,
            source = source,
            sender = sender,
            category = category,
            timestamp = timestamp,
            messageHash = hash
        )
    }

    /**
     * Check if a message sender looks like a financial institution.
     * Used as a pre-filter to avoid processing spam.
     */
    fun isLikelyFinancialSender(sender: String): Boolean {
        val lower = sender.lowercase()
        return FINANCIAL_SENDERS.any { lower.contains(it) }
    }

    /**
     * Check if a notification package is a known payment app.
     */
    fun isPaymentApp(packageName: String): Boolean {
        return UPI_APP_PACKAGES.contains(packageName)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private fun isFinancialDeduction(text: String): Boolean {
        val lower = text.lowercase()
        return DEBIT_KEYWORDS.any { lower.contains(it) }
    }

    private fun shouldExclude(text: String): Boolean {
        val lower = text.lowercase()

        // If contains deduction keyword AND exclusion, check which is primary
        val hasDeduction = DEBIT_KEYWORDS.any { lower.contains(it) }

        // Pure exclusion patterns (OTP, promotional, etc.)
        val exclusionKeywords = listOf(
            "otp", "one time password", "is your otp",
            "verification code", "is the code",
            "cashback", "offer expires", "avail offer",
            "click here", "download app", "install app",
            "congratulations", "you have won",
            "pre-approved", "you are eligible",
            "minimum due", "outstanding amount",
            "available balance", "current balance",
            "account balance is", "bal:",
            "login to your account", "signed in",
            "password changed", "pin changed",
            "aadhaar", "kyc update", "kyc pending",
            "nominee", "nomination"
        )

        val hasExclusion = exclusionKeywords.any { lower.contains(it) }

        // If it has OTP or purely promotional content, exclude regardless
        if (lower.contains("otp") || lower.contains("one time password")) return true

        // If it's a balance alert without deduction keyword
        if ((lower.contains("available balance") || lower.contains("account balance"))
            && !hasDeduction) return true

        // Promotional SMS patterns
        if (lower.contains("offer") && !hasDeduction) return true
        if (lower.contains("get ") && lower.contains("% off") && !hasDeduction) return true

        // Credit-only messages (no deduction)
        if (lower.contains("credited") && !hasDeduction) return true
        if (lower.contains("received") && lower.contains("from") && !hasDeduction) return true

        return hasExclusion && !hasDeduction
    }

    fun extractAmount(text: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues[1].replace(",", "")
            val value = raw.toDoubleOrNull() ?: continue
            if (value > 0) return value
        }
        return null
    }

    private fun extractBank(text: String, sender: String): String {
        val combined = "${text.lowercase()} ${sender.lowercase()}"
        for ((bankName, keywords) in BANK_PATTERNS) {
            if (keywords.any { combined.contains(it) }) return bankName
        }
        return ""
    }

    private fun extractMerchant(text: String): String {
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val candidate = match.groupValues[1].trim()
            if (candidate.length >= 3 && candidate.length <= 60) {
                // Clean up: remove trailing punctuation
                return candidate.trimEnd('.', ',', ';', ':').trim()
            }
        }
        return ""
    }

    private fun extractPaymentMethod(text: String): String {
        val lower = text.lowercase()
        for ((method, keywords) in PAYMENT_METHOD_PATTERNS) {
            if (keywords.any { lower.contains(it) }) return method
        }
        return "OTHER"
    }

    private fun extractAccountLast4(text: String): String {
        for (pattern in ACCOUNT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val digits = match.groupValues[1]
            if (digits.length == 4) return digits
        }
        return ""
    }

    private fun extractReference(text: String): String {
        for (pattern in REFERENCE_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val ref = match.groupValues[1].trim()
            if (ref.isNotEmpty()) return ref
        }
        return ""
    }

    private fun detectCategory(text: String, merchant: String): String {
        val combined = "${text.lowercase()} ${merchant.lowercase()}"
        for ((category, keywords) in CATEGORY_PATTERNS) {
            if (keywords.any { combined.contains(it) }) return category
        }
        return "Other"
    }

    fun computeHash(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
