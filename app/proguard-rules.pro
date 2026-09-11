# ══════════════════════════════════════════════════════════════════════════════
# Ol Saathi ProGuard / R8 Rules — Aggressive Release Shrinking
# Target: <180 MB heap, Android 9+ (API 28–34), ARM Cortex-A53/A55
# ══════════════════════════════════════════════════════════════════════════════

# ─── Aggressive Optimization Flags ──────────────────────────────────────────
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
-overloadaggressively

# ─── ONNX Runtime — Native C++ JNI bindings ────────────────────────────────
-keep class com.microsoft.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.extensions.** { *; }
-dontwarn com.microsoft.onnxruntime.**

# Preserve native method signatures for JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─── Application & Activity Classes ─────────────────────────────────────────
-keep class app.olsaathi.OlSaathiApplication { *; }
-keep class app.olsaathi.ui.** { *; }

# ─── FileProvider (XML-referenced) ──────────────────────────────────────────
-keep class androidx.core.content.FileProvider { *; }

# ─── Kotlin Coroutines ──────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
    <fields>;
}
-keepclassmembers class kotlin.coroutines.** {
    volatile <fields>;
}

# ─── Kotlin Serialization / Metadata ────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# ─── Enums ──────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Parcelable ─────────────────────────────────────────────────────────────
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ─── View Binding ───────────────────────────────────────────────────────────
-keep class **.databinding.** { *; }
-keep class **.databinding.**$* { *; }

# ─── Material Design ────────────────────────────────────────────────────────
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ─── Suppress Known Missing Classes ─────────────────────────────────────────
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn kotlin.Unit
-dontwarn kotlinx.coroutines.**
-dontwarn android.support.**

# ─── PdfBox-Android ─────────────────────────────────────────────────────────
# PdfBox references an optional JPEG2000 decoder it does not ship, so R8 fails
# the release build on the missing class. Lesson-import PDFs are text, and
# JPXFilter is only reached by a JPEG2000-encoded image, so warning off it is
# the whole fix. Without this the release build cannot be produced at all.
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
