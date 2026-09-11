# ── Kotlin / Coroutines ────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Compose Runtime ────────────────────────────────────────────────────
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ── Media3 / ExoPlayer ─────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**
# Service ถูกอ้างผ่าน ComponentName แบบ reflection-like จึงห้าม obfuscate ชื่อ
-keep class com.aurora.player.PlaybackService { *; }
-keep class com.aurora.player.MainActivity { *; }
-keep class com.aurora.player.AuroraApp { *; }

# ── Coil 3 ─────────────────────────────────────────────────────────────
-keep class coil3.** { *; }
-dontwarn coil3.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Domain models (ป้องกัน field ถูกตัดจากการ serialize ภายหลัง) ────────
-keep class com.aurora.player.core.** { *; }

# ── Guava (ListenableFuture ของ MediaController) ───────────────────────
-dontwarn com.google.common.**
-keep class com.google.common.util.concurrent.** { *; }

# ── ลด log ใน release ──────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
