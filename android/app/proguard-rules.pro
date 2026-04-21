# Add project specific ProGuard rules here.

-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
