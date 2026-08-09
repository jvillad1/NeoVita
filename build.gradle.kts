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

// `ws` es la única dependencia npm del toolchain que no se puede parchear regenerando el
// lock: los paquetes que Kotlin genera para wasm lo pinnean EXACTO en 8.18.0 y el engine.io
// de karma pide ~8.20.1, y los dos rangos excluyen la versión sin la vulnerabilidad.
//
// Los demás paquetes que marca Dependabot (fast-uri, js-yaml, socket.io-parser) NO llevan
// resolution a propósito: sus rangos (^3.0.1, ^4.1.0, ~4.2.4) ya admiten el parche, así que
// basta con `kotlinUpgradeYarnLock`. Fijarlos a una versión exacta fue un error — congela el
// paquete, y cuando salió una advisory nueva de fast-uri el pin impidió que se moviera.
//
// Nada de esto viaja en el bundle wasm: son dependencias de build (webpack y karma).
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        resolution("ws", "8.21.0")
    }
}
