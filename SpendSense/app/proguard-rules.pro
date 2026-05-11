# SpendSense ProGuard Rules

# Keep Room entities
-keep class com.spendsense.data.models.** { *; }
-keep class com.spendsense.data.db.** { *; }

# Keep ViewModel
-keep class com.spendsense.ui.viewmodel.** { *; }

# Room
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep service classes
-keep class com.spendsense.service.** { *; }
-keep class com.spendsense.detection.** { *; }
