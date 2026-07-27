import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }

    // Compose UI framework consumed by iosApp. baseName stays "ComposeApp" so the
    // Swift `import ComposeApp` and the generated .xcframework name are stable.
    val xcf = XCFramework("ComposeApp")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            xcf.add(this)
        }
    }

    // wasmJs is a library target here; the browser executable lives in :webApp.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // Needed directly here (not just transitively via :core) so DashboardViewModel
            // can (de)serialize ScreenDefinitionDto when writing/reading the local cache.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
            // api (not implementation): App()/sharedModule() expose core types
            // (LocalCache) in their public signatures, so consumers (webApp, androidApp,
            // iosApp) need core on their compile classpath transitively.
            api(project(":core"))
        }
        androidMain.dependencies {
            implementation("io.insert-koin:koin-android:4.0.0")
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation(libs.coil.network.okhttp)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            implementation(libs.firebase.messaging)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.neovita.app"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Bundled fonts (Roboto + Noto Emoji) live in commonMain/composeResources/font.
// Pin the generated accessor package so imports are stable across targets.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.neovita.app.resources"
}
