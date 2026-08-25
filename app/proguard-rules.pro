# ══════════════════════════════════════════════════════════════════════════════
# TribalFLN ProGuard / R8 Rules — Aggressive Release Shrinking
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

# ─── Room Database — Entities, DAOs, SQLite Vector Storage ──────────────────
-keep class in.gov.tribalfln.data.StudentEntity { *; }
-keep class in.gov.tribalfln.data.CompetencyScoreEntity { *; }
-keep class in.gov.tribalfln.data.WorksheetLogEntity { *; }
-keep class in.gov.tribalfln.data.ProgressDao { *; }
-keep class in.gov.tribalfln.data.StudentProgressDatabase { *; }
-keep class in.gov.tribalfln.data.CompetencyMastery { *; }
-keep class in.gov.tribalfln.data.LocalVectorDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }

# ─── Custom Canvas Views (XML-inflated) ─────────────────────────────────────
-keep class in.gov.tribalfln.ui.LearningGapRadarView { *; }
-keep class in.gov.tribalfln.OlChikiGlyphRenderer { *; }
-keep class in.gov.tribalfln.ui.LearningGapRadarView** { *; }
-keep class in.gov.tribalfln.OlChikiGlyphRenderer** { *; }

# ─── Application & Activity Classes ─────────────────────────────────────────
-keep class in.gov.tribalfln.TribalFLNApplication { *; }
-keep class in.gov.tribalfln.NipunEducatorDashboardActivity { *; }
-keep class in.gov.tribalfln.MainActivity { *; }

# ─── IME Service ────────────────────────────────────────────────────────────
-keep class in.gov.tribalfln.ime.TribalKeyboardService { *; }
-keep class * extends android.inputmethodservice.InputMethodService { *; }

# ─── Security / Encryption ──────────────────────────────────────────────────
-keep class in.gov.tribalfln.data.SecurityUtils { *; }
-keep class in.gov.tribalfln.data.SecurityUtils$EncryptedData { *; }

# ─── Wi-Fi P2P Mesh Sync ────────────────────────────────────────────────────
-keep class in.gov.tribalfln.mesh.ClassroomMeshSync { *; }
-keep class in.gov.tribalfln.mesh.ClassroomMeshSync$Event { *; }
-keep class in.gov.tribalfln.mesh.ClassroomMeshSync$Event$** { *; }

# ─── ONNX Model Runners ─────────────────────────────────────────────────────
-keep class in.gov.tribalfln.VoiceActivityDetectionInterceptor { *; }
-keep class in.gov.tribalfln.OfflineOcrScanner { *; }
-keep class in.gov.tribalfln.SemanticCurriculumSearchEngine { *; }

# ─── Audio / TTS / Voice ────────────────────────────────────────────────────
-keep class in.gov.tribalfln.OfflineTribalSpeechSynthesizer { *; }
-keep class in.gov.tribalfln.ClassroomAudioRecorder { *; }
-keep class in.gov.tribalfln.ClassroomNoiseFilter { *; }
-keep class in.gov.tribalfln.VoiceEngineManager { *; }
-keep class in.gov.tribalfln.VoiceEngineManager** { *; }
-keep class in.gov.tribalfln.CurriculumItem { *; }
-keep class in.gov.tribalfln.VoiceMatch { *; }

# ─── Worksheet PDF Generator ────────────────────────────────────────────────
-keep class in.gov.tribalfln.BilingualMaterialSynthesizer { *; }

# ─── Thermal Printer ────────────────────────────────────────────────────────
-keep class in.gov.tribalfln.BluetoothThermalPrinter { *; }

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

# ─── CameraX ────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

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
