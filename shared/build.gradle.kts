plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget()
    iosX64(); iosArm64(); iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.web.driver)
        }
        val nonJsMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(nonJsMain)
        iosMain.get().dependsOn(nonJsMain)
        val nonJsNativeMain by creating { dependsOn(nonJsMain) }
        iosMain.get().dependsOn(nonJsNativeMain)
        val appleMain by getting
        appleMain.dependencies { implementation(libs.ktor.client.darwin) }
        val jvmAndAndroidMain by creating { dependsOn(nonJsMain) }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmAndAndroidMain.dependencies { implementation(libs.ktor.client.cio) }
    }
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
