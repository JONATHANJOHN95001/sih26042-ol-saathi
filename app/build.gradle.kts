import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing credentials, read from local.properties or the environment, never
// committed. They used to sit in this file as string literals, which put them
// on a public repo the moment it was pushed. The keystore itself has always
// been gitignored, so nothing could be signed with them, but a password in
// public is a password to change.
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(key)

android {
    // Produces OlSaathi-SIH26042-v1.0.0-debug.apk rather than app-debug.apk,
    // so the file still says what it is once it has been mailed around.
    base.archivesName = "OlSaathi-SIH26042-v1.0.0"

    namespace = "app.olsaathi"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.olsaathi"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Bhashini, read from local.properties like the signing config above and
        // gitignored the same way. Only the inference key reaches the APK: the
        // user id and Udyat key exist to call a handshake the app does not need,
        // because its answer is fixed (see BhashiniClient).
        //
        // -PofflineOnly builds with no key at all, for the submission APK whose
        // Check and Proof screen has to show zero network calls. With no key the
        // client never opens a socket and the app behaves as if Bhashini did not
        // exist.
        val bhashiniKey =
            if (project.hasProperty("offlineOnly")) ""
            else (secret("BHASHINI_INFERENCE_KEY") ?: secret("BHASHINI_API_KEY") ?: "").trim()
        val bhashiniEndpoint =
            if (bhashiniKey.isEmpty()) ""
            else secret("BHASHINI_ENDPOINT")?.trim()
                ?: "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
        buildConfigField("String", "BHASHINI_API_KEY", "\"" + bhashiniKey + "\"")
        buildConfigField("String", "BHASHINI_ENDPOINT", "\"" + bhashiniEndpoint + "\"")
    }

    // Configured only when the keystore and its credentials are actually
    // present, so a contributor who has neither can still clone and build a
    // debug APK. A release build without them stays unsigned rather than
    // failing with a misleading error.
    val keystoreFile = file("release-key.jks")
    val hasSigning = keystoreFile.exists() &&
        secret("RELEASE_STORE_PASSWORD") != null &&
        secret("RELEASE_KEY_PASSWORD") != null

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS") ?: "tribalfln"
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // NetworkGuard.recordNetworkCall() logs through android.util.Log, which is a
    // stub that throws in JVM unit tests. The concurrency test for the offline
    // call counter has to call it thousands of times, so let the stubs return
    // defaults instead of throwing. Applies to unit tests only; nothing on a
    // device changes.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<Test> {
    workingDir = rootProject.projectDir
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Material Design 3
    implementation("com.google.android.material:material:1.11.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // PDF text extraction, for importing a teacher's own lesson PDF.
    //
    // Android's built-in PdfRenderer only rasterises pages to bitmaps; it
    // cannot read text, so it is useless for turning a lesson into worksheet
    // content. PdfBox-Android is the Apache 2.0 port of Apache PDFBox and
    // works entirely offline with no Play Services dependency, which matters
    // because cheap classroom tablets often have Play Services broken or
    // absent.
    //
    // It is the single largest thing in this APK after the fonts. That cost
    // is deliberate and measured rather than accidental: see the size note in
    // PROBLEM_STATEMENT_TRACEABILITY_MATRIX.md.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
