import java.util.Properties

// Signierschluessel fuer Release-Builds. android/key.properties ist NICHT eingecheckt
// (siehe .gitignore im Repo-Wurzelverzeichnis) und liegt zusammen mit der .jks-Datei
// im Backup - ein Update laesst sich nur mit demselben Schluessel installieren.
//
// Fehlt die Datei, faellt der Release-Build auf den Debug-Schluessel zurueck, damit
// `flutter run --release` auf einem frischen Checkout weiter funktioniert. Zum
// Verteilen taugt so ein Build nicht.
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasUploadKey = keystoreProperties.getProperty("storeFile") != null

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "de.finn.everythingapp"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // Einmal gewaehlt und danach unveraenderlich: Android erkennt eine
        // Installation an dieser ID. Eine Aenderung waere fuer bestehende
        // Installationen eine andere App - deinstallieren, lokale Daten weg.
        applicationId = "de.finn.everythingapp"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasUploadKey) {
                signingConfigs.getByName("upload")
            } else {
                logger.warn("key.properties fehlt - Release-Build wird mit dem Debug-Schluessel signiert und ist nicht verteilbar.")
                signingConfigs.getByName("debug")
            }
        }
    }
}

flutter {
    source = "../.."
}
