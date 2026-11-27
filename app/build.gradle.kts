import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "io.chaldeaprjkt.gamespace"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.chaldeaprjkt.gamespace"
        minSdk = 33
        // Device generation this fork targets: crDroid 10.x / Android 14.
        targetSdk = 34
        // Always beats the ROM-bundled GameSpace when installed as an update.
        versionCode = 999999
        versionName = "14.0-bg1"
    }

    signingConfigs {
        create("platform") {
            storeFile = file("platform.jks")
            storePassword = "android"
            keyAlias = "platform"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("platform")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("platform")
        }
    }

    buildFeatures {
        aidl = true
    }

    sourceSets {
        getByName("main") {
            aidl.srcDirs("src/main/java")
        }
    }

    // Java 8 language level is deliberate: it keeps -Xbootclasspath/p legal for
    // javac, which is how the hidden-API shadow stubs win over android.jar.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        disable += setOf(
            "ContentDescription", "QueryAllPackagesPermission", "ProtectedPermissions",
            "OldTargetApi", "UseCompatLoadingForDrawables", "UnsafeProtectedBroadcastReceiver",
            "AppCompatCustomView", "UseSwitchCompatOrMaterialCode", "TextFields"
        )
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

// Compile-time-only @hide stubs; shadowed over android.jar for javac. The jar
// output path is deterministic for a version-less java-library module, so we
// reference it as a path and only depend on the task by path (avoids eager
// cross-project task configuration).
val stubsJarPath = File(rootDir, "stubs/build/libs/stubs.jar").absolutePath

dependencies {
    compileOnly(project(":stubs"))

    implementation(project(":settingslib-compat"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
}

kapt {
    correctErrorTypes = true
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(":stubs:jar")
    options.compilerArgs.add("-Xbootclasspath/p:$stubsJarPath")
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(":stubs:jar")
}
