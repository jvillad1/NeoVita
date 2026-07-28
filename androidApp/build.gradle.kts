import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.neovita.app.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.neovita.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        // -PserverUrl=... overrides the default for either build type, e.g. for a physical
        // device on the same LAN: ./gradlew :androidApp:assembleDebug -PserverUrl=http://<LAN-IP>:8080
        getByName("debug") {
            val serverUrl = (project.findProperty("serverUrl") as String?) ?: "http://10.0.2.2:8080"
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            // Production default: the Railway-hosted NeoVita server.
            val serverUrl = (project.findProperty("serverUrl") as String?) ?: "https://neovita.up.railway.app"
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core"))
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.androidx.core.ktx)
    // shared's androidMain firebase-messaging dep is `implementation`, not visible here
    // transitively — NeoVitaMessagingService needs it directly on androidApp's classpath.
    implementation(libs.firebase.messaging)
    implementation("androidx.activity:activity-compose:1.9.3")
}
