import java.util.Properties
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

val localProperties =
    Properties().apply {
      val localPropertiesPath = rootDir.toPath() / "local.properties"
      if (localPropertiesPath.exists()) {
        load(localPropertiesPath.inputStream())
      }
    }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // The DAT SDK is served from GitHub Packages. It needs ANY GitHub personal
    // access token (classic) with the read:packages scope — put it in
    // local.properties as `github_token=ghp_...` or export GITHUB_TOKEN.
    maven {
      url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
      credentials {
        username = "" // not needed
        password = System.getenv("GITHUB_TOKEN") ?: localProperties.getProperty("github_token")
      }
    }
  }
}

rootProject.name = "DATPlayground"

include(":app")
