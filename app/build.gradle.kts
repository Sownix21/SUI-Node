plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

fun secret(name: String): String? = providers.gradleProperty(name)
  .orElse(providers.environmentVariable(name))
  .orNull
  ?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("SUI_RELEASE_STORE_FILE")
val releaseStorePassword = secret("SUI_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secret("SUI_RELEASE_KEY_ALIAS")
val releaseKeyPassword = secret("SUI_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
  releaseStoreFile,
  releaseStorePassword,
  releaseKeyAlias,
  releaseKeyPassword,
).all { it != null }
require(hasReleaseSigning || listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it == null }) {
  "Incomplete release signing configuration. Set all four SUI_RELEASE_* values, or omit all four for an unsigned release."
}

kotlin {
  compilerOptions {
    optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
  }
}

android {
  namespace = "com.sonix21.suinode"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.sonix21.suinode"
    minSdk = 26
    targetSdk = 36
    versionCode = 5
    versionName = "2.3"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(checkNotNull(releaseStoreFile))
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
      if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
    }
    debug { }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation(libs.okhttp)
  implementation(libs.zxing.core)
  implementation("org.snakeyaml:snakeyaml-engine:2.10")
  implementation(libs.androidx.biometric)
  // Legacy Fragment (pulled by Biometric) rejects modern Activity Result request codes.
  implementation("androidx.fragment:fragment-ktx:1.8.9")
  implementation(libs.androidx.security.crypto)
  debugImplementation(libs.androidx.compose.ui.tooling)
  testImplementation(libs.junit)
  testImplementation(libs.json)
  testImplementation("com.squareup.okhttp3:okhttp-tls:4.10.0")
}
