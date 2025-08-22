plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.0.20"
}

android {
    namespace = "com.currand60.wprimebalance"
    compileSdk = 36
    testOptions { unitTests.all { it.useJUnitPlatform() } }

    defaultConfig {
        applicationId = "com.currand60.wprimebalance"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.4"
        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false // Or true, depending on your needs
            isDebuggable = true // Make sure this is true for debug builds
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            merges += "META-INF/LICENSE.md"
            merges += "META-INF/LICENSE-notice.md"
        }
    }
}

tasks.register("generateManifest") {
    description = "Generates manifest.json with current version information"
    group = "build"

    doLast {
        val manifestFile = file("$projectDir/manifest.json")
        val manifest = mapOf(
            "label" to "W' Balance",
            "packageName" to "com.currand60.wprimebalance",
            "latestApkUrl" to "https://github.com/currand/karoo-wprimebalance/releases/latest/download/app-release.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "currand60",
            "description" to "Calculates W' Balance during the ride and displays it in several formats",
        )

        val gson = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(gson)
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")
    }
}

tasks.named("assemble") {
    dependsOn("generateManifest")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.javax.inject)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.preview)
    implementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.mockk)
    implementation(libs.androidx.navigation.runtime.android)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter)
}
