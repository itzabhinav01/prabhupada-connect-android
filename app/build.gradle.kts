import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing material is never committed (see .gitignore). Locally, it
// comes from keystore/keystore.properties (created once via `keytool`,
// pointing at a sibling .jks in the same gitignored directory); in CI or on
// a build server, from RELEASE_* environment variables instead. Neither
// being present (e.g. a fresh CI checkout that only runs assembleDebug)
// simply means no "release" signingConfig is registered below, so
// assembleRelease still succeeds - it just produces an unsigned APK.
val keystorePropertiesFile = rootProject.file("keystore/keystore.properties")
var releaseStoreFile: File? = null
var releaseStorePassword: String? = null
var releaseKeyAlias: String? = null
var releaseKeyPassword: String? = null

if (keystorePropertiesFile.exists()) {
    val props = Properties().apply { load(keystorePropertiesFile.inputStream()) }
    releaseStoreFile = rootProject.file("keystore/${props.getProperty("storeFile")}")
    releaseStorePassword = props.getProperty("storePassword")
    releaseKeyAlias = props.getProperty("keyAlias")
    releaseKeyPassword = props.getProperty("keyPassword")
} else if (System.getenv("RELEASE_STORE_FILE") != null) {
    releaseStoreFile = file(System.getenv("RELEASE_STORE_FILE")!!)
    releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
    releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
    releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
}

android {
    namespace = "com.prabhupadaconnect.vedabase"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prabhupadaconnect.vedabase"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The read-only canonical corpus ships pre-built and compressed in
        // assets/ (see app/src/main/assets/README.md) - never built at
        // compile time and never bundled through Room, since it is a fixed,
        // pre-baked artifact swapped only across full corpus releases.
        androidResources {
            noCompress += "db"
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)

    // The corpus is queried with FTS5 (see FtsQueryParser/CorpusRepository),
    // but Android's OS-provided SQLite build does not reliably include FTS5
    // across devices/versions - this bundles a native SQLite that always
    // has it compiled in, as a drop-in for android.database.sqlite.*.
    implementation(libs.requery.sqlite.android)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
