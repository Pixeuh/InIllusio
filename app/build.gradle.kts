import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Beta signing material is local and intentionally never committed.
val betaSigningFile = rootProject.file("beta-signing.properties")
val betaSigningProperties = Properties().apply {
    if (betaSigningFile.isFile) betaSigningFile.inputStream().use { load(it) }
}
val betaStorePath = betaSigningProperties.getProperty("storeFile").orEmpty()
val betaStorePassword = betaSigningProperties.getProperty("storePassword").orEmpty()
val betaKeyAlias = betaSigningProperties.getProperty("keyAlias").orEmpty()
val betaKeyPassword = betaSigningProperties.getProperty("keyPassword").orEmpty()
val betaStore = betaStorePath.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
val betaSigningConfigured = betaStore != null && betaStore.isFile &&
    betaStorePassword.isNotBlank() && betaKeyAlias.isNotBlank() && betaKeyPassword.isNotBlank()

android {
    namespace = "fr.inillusio"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.inillusio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val testRewardedUnitId = "ca-app-pub-3940256099942544/5224354917"
    val releaseAppId = providers.gradleProperty("ADMOB_APP_ID").orNull.orEmpty()
    val releaseRewardedId = providers.gradleProperty("ADMOB_REWARDED_UNIT_ID").orNull.orEmpty()
    val releaseAdsEnabled = providers.gradleProperty("ADMOB_RELEASE_ENABLED").orNull == "true" &&
        providers.gradleProperty("ADMOB_PRIVACY_CONFIGURED").orNull == "true" &&
        releaseAppId.isNotBlank() && releaseRewardedId.isNotBlank()

    buildTypes {
        debug {
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$testRewardedUnitId\"")
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
        }
        release {
            buildConfigField("boolean", "ADS_ENABLED", releaseAdsEnabled.toString())
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"${if (releaseAdsEnabled) releaseRewardedId else ""}\"")
            manifestPlaceholders["ADMOB_APP_ID"] = if (releaseAdsEnabled) releaseAppId else ""
        }
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$testRewardedUnitId\"")
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
            if (betaSigningConfigured) {
                signingConfig = signingConfigs.create("beta").apply {
                    storeFile = betaStore
                    storePassword = betaStorePassword
                    keyAlias = betaKeyAlias
                    keyPassword = betaKeyPassword
                }
            }
        }
    }

    sourceSets.getByName("main").assets.setSrcDirs(listOf(rootProject.file("data")))
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
    // The release facade has no UI or mutating developer operations. Reuse it in beta.
    sourceSets.getByName("beta").java.srcDir("src/release/java")
    // Developer-specific unit/instrumentation tests belong only to the debug variant.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

tasks.configureEach {
    if (name in setOf("packageBeta", "signBetaBundle", "packageBetaBundle", "packageBetaUniversalApk")) {
        doFirst {
            if (!betaSigningConfigured) {
                throw GradleException(
                    "La variante bêta exige une clé dédiée. Créez beta-signing.properties localement " +
                        "en suivant docs/BETA.md ; aucune clé DEBUG ne sera utilisée."
                )
            }
        }
    }
}

tasks.configureEach {
    if (name == "assembleBeta") {
        doLast {
            val apk = layout.buildDirectory.file("outputs/apk/beta/app-beta.apk").get().asFile
            if (apk.isFile) {
                val destination = rootProject.file("dist")
                destination.mkdirs()
                val betaVersion = android.defaultConfig.versionName + android.buildTypes.getByName("beta").versionNameSuffix
                apk.copyTo(destination.resolve("InIllusio-v$betaVersion.apk"), overwrite = true)
            }
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.google.play.services.ads)
    implementation(libs.google.ump)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
