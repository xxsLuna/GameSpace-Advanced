// Compile-time-only stubs for @hide / framework-internal classes that the ROM
// provides at runtime (bootclasspath). Never packaged into the APK: the app
// module consumes this jar as compileOnly (+ -Xbootclasspath/p so the shadowed
// android.* classes win over the SDK's android.jar).
import java.util.Properties

plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// android.jar for compiling the stubs themselves (compileOnly: stubs are
// replaced by the real framework at runtime).
val sdkDir: String? = run {
    val local = File(rootDir, "local.properties")
    if (local.exists()) {
        val props = Properties()
        local.inputStream().use { props.load(it) }
        props.getProperty("sdk.dir")?.replace("\\", "/")
    } else null
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
}

dependencies {
    if (sdkDir != null) {
        compileOnly(files("$sdkDir/platforms/android-35/android.jar"))
    } else {
        throw GradleException("Android SDK not found: set sdk.dir in local.properties")
    }
}
