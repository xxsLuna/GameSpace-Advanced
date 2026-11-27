// Minimal runtime replacement for the AOSP SettingsLib classes GameSpace
// statically linked inside the ROM build (CollapsingToolbarBaseActivity,
// IllustrationPreference, MainSwitchPreference) plus the LineagePreferenceLib
// base class (SelfRemovingSwitchPreference). These ARE packaged into the APK.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.settingslib"
    compileSdk = 35

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    api("androidx.appcompat:appcompat:1.7.0")
    api("androidx.preference:preference-ktx:1.2.1")
    api("com.google.android.material:material:1.12.0")
}
