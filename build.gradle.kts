plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.sqldelight).apply(false)
    alias(libs.plugins.ktor).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
}

// Dependencias npm del toolchain de Kotlin/Wasm (webpack y su dev-server). Dependabot
// marcó cuatro advisories HIGH en kotlin-js-store/yarn.lock; regenerar el lock arregla
// tres, pero `ws` viene pinneado exacto (8.18.0) y por rango cerrado (~8.20.1) desde
// dentro del toolchain, así que sin forzar la resolución se queda vulnerable.
// Sólo afecta al build: nada de esto viaja en el bundle wasm que sirve el servidor.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        resolution("ws", "8.21.0")
        resolution("fast-uri", "3.1.3")
        resolution("js-yaml", "4.3.1")
        resolution("socket.io-parser", "4.2.7")
    }
}
