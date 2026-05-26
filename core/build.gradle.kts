import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
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
    iosX64(); iosArm64(); iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // SQLDelight has no wasmJs artifact. Insert a "nonWasm" intermediate between
    // commonMain and every non-wasm target, so all SQLDelight code (the generated
    // NeoVitaDatabase + the driver-backed LocalCache) is shared by android + ios only.
    // commonMain stays database-free, which lets wasmJs compile.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonWasm") {
                withAndroidTarget()
                group("apple") {
                    group("ios") {
                        withIosArm64()
                        withIosX64()
                        withIosSimulatorArm64()
                    }
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        val nonWasmMain by getting {
            dependencies {
                implementation(libs.sqldelight.coroutines)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

// SQLDelight 2.0.2 publishes no wasmJs variant. Exclude its group from every wasmJs
// configuration so dependency resolution for the web target does not fail.
configurations.configureEach {
    if (name.startsWith("wasmJs")) {
        exclude(group = "app.cash.sqldelight")
    }
}

// SQLDelight generates its Kotlin under commonMain, so wasmJs (which inherits from
// commonMain) would try to compile it and fail because the runtime is excluded above.
// Move the generated dir from commonMain to nonWasmMain so only android/ios see it.
// A Provider-based srcDir keeps task-dependency wiring intact.
afterEvaluate {
    val generateTask = tasks.named("generateCommonMainNeoVitaDatabaseInterface")
    val generatedDirProvider = generateTask.map {
        layout.buildDirectory.dir("generated/sqldelight/code/NeoVitaDatabase/commonMain").get()
    }

    val commonMain = kotlin.sourceSets.getByName("commonMain")
    val nonWasmMain = kotlin.sourceSets.getByName("nonWasmMain")

    val toRemove = commonMain.kotlin.srcDirs.filter { it.path.contains("generated/sqldelight") }.toSet()
    if (toRemove.isNotEmpty()) {
        commonMain.kotlin.setSrcDirs(commonMain.kotlin.srcDirs - toRemove)
    }
    nonWasmMain.kotlin.srcDir(generatedDirProvider)
}

android {
    namespace = "com.neovita.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("NeoVitaDatabase") {
            packageName.set("com.neovita.shared.db")
        }
    }
}
