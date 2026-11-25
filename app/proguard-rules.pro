# Utto R8 / ProGuard Rules — targeted keeps only so unused code is aggressively stripped

# Keep Android entry points registered in AndroidManifest.xml
-keep class com.example.MainActivity { *; }
-keep class com.example.alarm.AlarmRingingActivity { *; }
-keep class com.example.alarm.AlarmRingingService { *; }
-keep class com.example.alarm.AlarmTriggerReceiver { *; }
-keep class com.example.alarm.BootAndTimeChangeReceiver { *; }
-keep class com.example.widget.SolarSummaryWidgetProvider { *; }

# Keep ViewModel reflective constructor used by AndroidViewModelFactory
-keepclassmembers class com.example.viewmodel.UttoViewModel {
    public <init>(android.app.Application);
}

# Keep enum lookup methods on persisted data enums
-keepclassmembers enum com.example.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
