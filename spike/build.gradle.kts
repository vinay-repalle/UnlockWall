// Phase 1 throwaway logging app. Delete this module once the experiments are done.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.vinayrepalle.unlockwall.spike"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "io.github.vinayrepalle.unlockwall.spike"
        // 35 so it also installs on the Android 15 test phone.
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
}
