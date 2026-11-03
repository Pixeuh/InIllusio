plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

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
    }

    sourceSets.getByName("main").assets.setSrcDirs(listOf(rootProject.file("data")))

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

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
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
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
