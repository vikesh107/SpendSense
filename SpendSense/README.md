# SpendSense 💸
### Fully Offline Android Expense Tracker

Automatically detects and logs all real financial deductions from SMS messages and payment app notifications. **100% on-device. Zero internet. Zero cloud.**

---

## 🏗️ Project Structure

```
SpendSense/
├── app/
│   ├── build.gradle                          # Dependencies (Room, Lifecycle, Material)
│   └── src/main/
│       ├── AndroidManifest.xml               # Permissions + Services
│       └── java/com/spendsense/
│           ├── data/
│           │   ├── db/
│           │   │   ├── SpendSenseDatabase.kt # Room SQLite database
│           │   │   └── TransactionDao.kt     # All DB queries
│           │   ├── models/
│           │   │   └── Transaction.kt        # Data entity
│           │   └── repository/
│           │       └── TransactionRepository.kt
│           ├── detection/
│           │   └── TransactionDetectionEngine.kt  # ⭐ Core parser
│           ├── service/
│           │   ├── SmsReceiver.kt            # Incoming SMS handler
│           │   ├── NotificationListenerService.kt # Payment app notifications
│           │   ├── SmsHistoricalImporter.kt  # One-time inbox scan
│           │   ├── BootReceiver.kt           # Restart after reboot
│           │   └── TransactionNotificationHelper.kt
│           └── ui/
│               ├── MainActivity.kt           # Expense list screen
│               ├── TransactionDetailActivity.kt
│               ├── adapters/
│               │   └── TransactionAdapter.kt
│               └── viewmodel/
│                   └── MainViewModel.kt
```

---

## 🔧 How to Build

### Prerequisites
- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17**
- **Android SDK** API 26+ (Android 8.0 Oreo minimum)
- Target: API 34 (Android 14)

### Steps

1. **Clone / extract** this project folder
2. Open in **Android Studio** → File → Open → select `SpendSense/` folder
3. Wait for Gradle sync to complete
4. Connect Android device (API 26+) or create AVD
5. Run: **Build → Generate Signed APK** or press ▶️

### Build Command (CLI)
```bash
cd SpendSense
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease  # Requires signing config
```

---

## 📱 Setup on Device

### Required Permissions

| Permission | Purpose | How to Grant |
|---|---|---|
| `READ_SMS` | Read bank transaction SMS | Prompted on first launch |
| `RECEIVE_SMS` | Detect new SMS instantly | Prompted on first launch |
| Notification Access | Payment app notifications | Settings → Notification Access |

### First Launch Flow

1. **Grant SMS permission** when prompted
2. **Enable Notification Access** (optional, for Google Pay/PhonePe/Paytm)
   - Goes to: Settings → Apps → Special App Access → Notification Access → SpendSense ✓
3. **Import historical SMS** — scans your inbox for past bank transactions
4. **Done!** App runs fully automatically from here

---

## ⚙️ How It Works

### Transaction Detection Pipeline

```
SMS/Notification arrives
        ↓
Pre-filter: Is sender a bank/payment app?
        ↓
Debit keyword check: "debited", "spent", "paid", "UPI txn"...
        ↓
Exclusion filter: Reject OTPs, cashbacks, balance alerts, promos
        ↓
Amount extraction: Regex parses ₹/Rs/INR amounts
        ↓
Sanity check: Amount between ₹1 and ₹1 crore?
        ↓
Extract: Bank, merchant, payment method, account, reference ID
        ↓
Duplicate check: SHA-256 hash of message body
        ↓
Save to local SQLite via Room
        ↓
Show in expense list + local notification
```

### Supported Transaction Types

| Type | Examples |
|---|---|
| Bank debit SMS | "Rs 500 debited from A/c xx1234" |
| UPI payments | "UPI txn of Rs 200 sent to merchant@upi" |
| Debit card | "Purchase of Rs 1500 at AMAZON using HDFC Debit Card" |
| Credit card | "Rs 3000 spent on SBI Credit Card at FLIPKART" |
| ATM withdrawal | "Cash withdrawal of Rs 5000 from ATM" |
| FASTag | "FASTag toll deducted Rs 65 at NH-48" |
| EMI | "EMI of Rs 8500 debited for loan account" |
| NACH/Auto-debit | "Auto-debit mandate executed for Rs 2000" |
| Subscription | "Netflix subscription charge of Rs 649" |
| Wallet | "Rs 100 debited from Paytm Wallet" |

### Detected Banks (50+)
HDFC, SBI, ICICI, Axis, Kotak, PNB, BOB, Canara, Union, Yes, IndusInd, Federal, IDBI, IOB, BOI, RBL, DCB, Bandhan, IDFC First, AU Small Finance, Paytm Payments Bank, Airtel Payments Bank, and more.

### Payment Apps Monitored (Notifications)
Google Pay, PhonePe, Paytm, BHIM, Amazon Pay, MobiKwik, FreeCharge, HDFC MobileBanking, ICICI iMobile, SBI YONO, Kotak Mobile, WhatsApp Pay, and more.

---

## 🔒 Privacy Architecture

```
Device Storage (SQLite)
       ↑
Transaction Detection Engine (on-device regex)
       ↑
SMS Receiver / Notification Listener
       ↑
Incoming messages (never leaves device)
```

- **No INTERNET permission** in manifest — network calls are impossible
- **No cloud sync** — Room database stored in `/data/data/com.spendsense/`
- **No analytics** — no Firebase, no Crashlytics, no tracking SDKs
- **No accounts** — no login, no registration
- **Auto-backup disabled** — `android:allowBackup="false"`
- **Cloud backup excluded** — `backup_rules.xml` blocks DB from Google Backup

---

## 🗄️ Database Schema

```sql
CREATE TABLE transactions (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    amount                REAL NOT NULL,
    bank_name             TEXT DEFAULT '',
    merchant              TEXT DEFAULT '',
    payment_method        TEXT DEFAULT '',
    account_last4         TEXT DEFAULT '',
    transaction_reference TEXT DEFAULT '',
    sms_body              TEXT NOT NULL,
    source                TEXT DEFAULT 'SMS',
    sender                TEXT DEFAULT '',
    category              TEXT DEFAULT 'Other',
    timestamp             INTEGER NOT NULL,
    message_hash          TEXT UNIQUE NOT NULL  -- SHA-256, prevents duplicates
);

CREATE INDEX idx_timestamp ON transactions(timestamp);
CREATE INDEX idx_method ON transactions(payment_method);
CREATE INDEX idx_bank ON transactions(bank_name);
```

---

## 📐 Architecture

**MVVM + Repository Pattern**

```
UI Layer (Activities/Adapters)
         ↕ LiveData/StateFlow
ViewModel Layer (MainViewModel)
         ↕ suspend functions
Repository Layer (TransactionRepository)
         ↕ DAO
Database Layer (Room / SQLite)
```

**Background Services:**
- `SmsReceiver` — BroadcastReceiver for real-time SMS
- `NotificationListenerService` — System service for app notifications
- `SmsHistoricalImporter` — Coroutine-based one-time inbox scan
- `BootReceiver` — Restores monitoring after device restart

---

## 🚀 Key Files to Understand

### `TransactionDetectionEngine.kt`
The brain of the app. Pure Kotlin object, zero Android dependencies. Contains all regex patterns, keyword lists, and extraction logic. Fully unit-testable.

### `SmsReceiver.kt`
Registered as a BroadcastReceiver for `SMS_RECEIVED`. Groups multi-part SMS, calls the engine, saves results.

### `NotificationListenerService.kt`
System-level service that reads payment app notifications. Requires explicit user permission in Settings.

### `SmsHistoricalImporter.kt`
Queries `content://sms/inbox` to scan existing SMS. Called once on first launch.

---

## 🧪 Testing the Detection Engine

Add this to a test file:

```kotlin
@Test
fun testHdfcDebitSms() {
    val sms = "Rs.1500.00 debited from A/c XX1234 on 10-01-24 to VPA merchant@paytm. UPI Ref:123456789012."
    val result = TransactionDetectionEngine.analyze(sms, "HDFCBK", "SMS")
    
    assertNotNull(result)
    assertEquals(1500.0, result!!.amount, 0.01)
    assertEquals("HDFC Bank", result.bankName)
    assertEquals("UPI", result.paymentMethod)
    assertEquals("1234", result.accountLast4)
}

@Test
fun testOtpRejection() {
    val sms = "Your OTP for transaction is 123456. Valid for 10 minutes."
    val result = TransactionDetectionEngine.analyze(sms)
    assertNull(result)  // Must be rejected
}
```

---

## 📋 Permissions Explained

```xml
<!-- Read existing SMS -->
<uses-permission android:name="android.permission.READ_SMS" />

<!-- Receive new SMS in real-time -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />

<!-- Listen to payment notifications (granted in Settings) -->
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />

<!-- Restart monitoring after device reboot -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Show new transaction alerts -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- NO INTERNET PERMISSION — by design -->
```

---

## 🛠️ Customization

### Add a new bank
In `TransactionDetectionEngine.kt`, add to `BANK_PATTERNS`:
```kotlin
"My New Bank" to listOf("mynewbank", "mnb"),
```

### Add a new category
In `CATEGORY_PATTERNS`:
```kotlin
"Gaming" to listOf("steam", "playstation", "xbox", "google play games"),
```

### Add a payment app for notification monitoring
In `UPI_APP_PACKAGES`:
```kotlin
"com.newpayapp.android",
```

---

## 📦 Dependencies

| Library | Version | Purpose |
|---|---|---|
| Room | 2.6.1 | Local SQLite ORM |
| Lifecycle/ViewModel | 2.7.0 | MVVM architecture |
| Kotlin Coroutines | 1.7.3 | Async background work |
| Material Components | 1.11.0 | UI components |
| Security Crypto | 1.1.0-alpha06 | Optional encryption |
| WorkManager | 2.9.0 | Durable background tasks |

**No network dependencies. No analytics. No tracking.**

---

## ⚠️ Known Considerations

1. **SMS default app**: On Android 10+, only the default SMS app can read SMS in foreground. This app uses `READ_SMS` which works for background reading from the content provider.

2. **Notification access**: Must be manually granted in Settings. There's no programmatic way to grant this — it requires user action.

3. **Battery optimization**: If the device aggressively kills background processes, add SpendSense to battery optimization whitelist (Settings → Battery → Unrestricted).

4. **Multi-SIM**: The app processes SMS from all SIMs automatically.

5. **Android 12+ restrictions**: Some OEMs restrict background SMS reading. The historical import covers this gap.

---

## 📄 License

MIT License — Free to use, modify, and distribute.
