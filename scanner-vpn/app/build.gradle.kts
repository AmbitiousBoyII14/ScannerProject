plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.scannervpn.android"
    minSdk = 24
    targetSdk = 36
    versionCode = 10
    versionName = "1.9"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  splits.abi {
    isEnable = true
    reset()
    include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    isUniversalApk = false
  }
  packaging.jniLibs.useLegacyPackaging = true
  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation("org.java-websocket:Java-WebSocket:1.6.0")
  implementation("com.github.mwiede:jsch:2.28.8")
  implementation(files("libs/libv2ray.aar"))
  testImplementation(libs.junit)
  testImplementation("org.json:json:20240303")
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
}
