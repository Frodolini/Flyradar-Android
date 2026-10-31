import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Signeringsnøkkelen ligg UTANFOR repoet. Passord og sti blir lesne frå
// keystore.properties (som er lista i .gitignore). Finst ikkje fila, blir
// release-bygget usignert.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "no.frodolini.flyradar"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.frodolini.flyradar"
        minSdk = 26          // Android 8.0
        targetSdk = 35
        // Kvar ny versjon MÅ ha høgare versionCode, elles vil ikkje telefonen oppdatere.
        versionCode = 5
        versionName = "1.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// APK-fila får namn etter versjonen, t.d. Flyradar-1.0-release.apk
base {
    archivesName.set("Flyradar-${android.defaultConfig.versionName}")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    // Kartet: OpenStreetMap utan API-nøkkel
    implementation(libs.osmdroid)

    // Testar: vanlege JUnit-testar. org.json trengst fordi Android sin
    // utgåve av biblioteket ikkje finst når testane køyrer på PC-en.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    // Robolectric køyrer Android-kode utan telefon, og kan ta skjermbilete av appen
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
