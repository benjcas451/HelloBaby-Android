import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Compose-Compiler; Kotlin selbst kommt ueber AGPs Built-in Kotlin.
    alias(libs.plugins.kotlin.compose)
}

// Signing-Daten aus key.properties im Projekt-Root laden (nicht im Git,
// siehe .gitignore und key.properties.example). Derselbe Upload-Key wie bei
// der bisherigen Flutter-App – Play verlangt fuer Updates derselben
// applicationId denselben Schluessel.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "ch.tschir.hellobaby"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Gleiche applicationId wie die bisherige Flutter-App: die native App
        // ersetzt sie als Update (Sideloading) – Bestandsdaten und
        // -einstellungen bleiben erhalten.
        applicationId = "ch.tschir.HelloBaby"
        minSdk = 24
        targetSdk = 37
        // Muss ueber dem versionCode der installierten Flutter-App liegen
        // (zuletzt 7); Updates per Sideloading verlangen steigende Codes.
        // Die CI uebergibt -PbuildNumber=100+run_number, lokal gilt der Fallback.
        versionCode = (findProperty("buildNumber") as String?)?.toIntOrNull() ?: 9
        versionName = "3.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                // Absolute Pfade unveraendert, relative relativ zum Root.
                val configured = File(keystoreProperties["storeFile"] as String)
                storeFile = if (configured.isAbsolute) configured else rootProject.file(configured.path)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // R8 verkleinert das Bundle deutlich und optimiert den Bytecode;
            // das wear-Modul nutzt es laengst (dort gemessen: 7,0 -> 2,9 MB).
            // Play verlangt die Codeoptimierung nun auch fuer die Handy-App.
            // Keep-Regeln: src/main/keepRules/rules.keep. Die Mapping-Datei
            // legt AGP automatisch ins AAB.
            optimization {
                enable = true
            }
            // Ohne key.properties mit dem Debug-Key signieren, damit sich ein
            // Release-Build lokal auch ohne Keystore erzeugen laesst.
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time auf minSdk 24 (API < 26) braucht Library-Desugaring.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // REST-API (unterstuetzt mTLS, anders als HttpURLConnection).
    implementation(libs.okhttp)
    // Bilder (lokal + Server-Thumbnails).
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)
    // Videoplayer.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    // Video-Streams ueber denselben OkHttp-Client wie der Rest der App, damit
    // Auth-Kopfzeilen und Client-Zertifikat auch beim Abspielen greifen.
    implementation(libs.media3.datasource.okhttp)
    // Data-Layer-API: Anfragen der Wear-OS-App (WearRequestService).
    implementation(libs.play.services.wearable)
    // Zertifikats-Ordner via Storage Access Framework.
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
