# GoalWidget ProGuard rules
-keepattributes *Annotation*
-keep class com.goalwidget.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
