import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localSecret(name: String): String =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull
        ?: ""
fun quoted(value: String): String = "\"" + value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n") + "\""

val openRouterApi = localSecret("OPENROUTER_API")
// Public builds intentionally have no fictitious deployed backend; users configure their own server.
val backendUrl = localSecret("CHESHERS_BACKEND_URL")
val signingStoreFilePath = localSecret("CHESHERS_STORE_FILE")
val signingStorePassword = localSecret("CHESHERS_STORE_PASSWORD")
val signingKeyAlias = localSecret("CHESHERS_KEY_ALIAS").ifBlank { "cheshers" }
val signingKeyPassword = localSecret("CHESHERS_KEY_PASSWORD")
val hasReleaseSigning = signingStoreFilePath.isNotBlank() && File(signingStoreFilePath).exists() &&
    signingStorePassword.isNotEmpty() && signingKeyPassword.isNotEmpty()

android {
    namespace = "app.cheshers"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.cheshers"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "OPENROUTER_API", quoted(openRouterApi))
        buildConfigField("String", "BACKEND_URL", quoted(backendUrl))
        buildConfigField("boolean", "OPENROUTER_KEY_BUNDLED", "${openRouterApi.isNotBlank()}")
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(signingStoreFilePath)
                this.storePassword = signingStorePassword
                this.keyAlias = signingKeyAlias
                this.keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "BACKEND_URL", quoted(localSecret("CHESHERS_BACKEND_URL").ifBlank { "http://10.0.2.2:3000" }))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") } }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(project(":core-chess"))
    implementation(project(":core-ai"))
    implementation(project(":core-data"))
    implementation(project(":feature-game"))
    implementation(project(":feature-quests"))
    implementation(project(":feature-profile"))
    implementation(project(":feature-online"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
