object Versions {
    const val compileSdk = 35
    const val minSdk = 24
    const val targetSdk = 35
    const val agp = "8.5.0"
    const val kotlin = "2.0.0"
    const val composeBom = "2024.06.00"
    const val camerax = "1.3.4"
    const val lifecycle = "2.8.3"
    const val navigation = "2.7.7"
    const val coroutines = "1.8.1"
    const val pdfbox = "2.0.27.0"
    const val accompanist = "0.34.0"
    const val ndk = "26.3.11579264"
    const val cmake = "3.22.1"
}

object Deps {
    const val coreKtx = "androidx.core:core-ktx:1.13.1"
    const val activityCompose = "androidx.activity:activity-compose:1.9.0"
    const val lifecycleRuntime = "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}"
    const val lifecycleViewmodel = "androidx.lifecycle:lifecycle-viewmodel-compose:${Versions.lifecycle}"
    const val navigationCompose = "androidx.navigation:navigation-compose:${Versions.navigation}"

    const val composeBom = "androidx.compose:compose-bom:${Versions.composeBom}"
    const val composeUi = "androidx.compose.ui:ui"
    const val composeUiGraphics = "androidx.compose.ui:ui-graphics"
    const val composeUiToolingPreview = "androidx.compose.ui:ui-tooling-preview"
    const val composeMaterial3 = "androidx.compose.material3:material3"
    const val composeUiTooling = "androidx.compose.ui:ui-tooling"

    const val cameraCore = "androidx.camera:camera-core:${Versions.camerax}"
    const val cameraCamera2 = "androidx.camera:camera-camera2:${Versions.camerax}"
    const val cameraLifecycle = "androidx.camera:camera-lifecycle:${Versions.camerax}"
    const val cameraView = "androidx.camera:camera-view:${Versions.camerax}"

    const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
    const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}"

    const val pdfboxAndroid = "com.tom-roush:pdfbox-android:${Versions.pdfbox}"
    const val accompanistPermissions = "com.google.accompanist:accompanist-permissions:${Versions.accompanist}"
}
