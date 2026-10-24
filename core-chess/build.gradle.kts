plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(libs.chesslib)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
