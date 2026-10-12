# Library consumer rules (Compose, Coil, AndroidX) cover the rest.

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
