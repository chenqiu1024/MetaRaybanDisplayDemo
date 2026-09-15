import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

val localProperties =
    Properties().apply {
      val localPropertiesFile = rootProject.file("local.properties")
      if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
      }
    }

android {
  namespace = "com.example.datplayground"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.example.datplayground"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    // Developer Mode builds can use "0" for both. Production builds need real
    // credentials from the Wearables Developer Center.
    manifestPlaceholders["mwdat_application_id"] =
        providers.gradleProperty("mwdat_application_id").orNull
            ?: localProperties.getProperty("mwdat_application_id", "0")
    manifestPlaceholders["mwdat_client_token"] =
        providers.gradleProperty("mwdat_client_token").orNull
            ?: localProperties.getProperty("mwdat_client_token", "0")
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.material3)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.camera)
  implementation(libs.mwdat.display)
  implementation(libs.mwdat.mockdevice)
}
