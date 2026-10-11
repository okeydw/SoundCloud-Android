plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersionName = "0.4.4"
val appVersionCode = 9

android {
    namespace = "com.scd.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scd.android"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    val ciKeystore = System.getenv("KEYSTORE_FILE")

    signingConfigs {
        if (ciKeystore != null) {
            create("release") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "ui"
    productFlavors {
        create("lite") {
            dimension = "ui"
            isDefault = true
            manifestPlaceholders["iconViolet"] = "false"
            manifestPlaceholders["iconBlue"] = "true"
            manifestPlaceholders["iconOrange"] = "false"
        }
        create("full") {
            dimension = "ui"
            applicationIdSuffix = ".full"
            manifestPlaceholders["iconViolet"] = "true"
            manifestPlaceholders["iconBlue"] = "false"
            manifestPlaceholders["iconOrange"] = "false"
        }
    }

    sourceSets {
        getByName("lite") {
            java.srcDir("src/shared/java")
        }
        getByName("full") {
            java.srcDir("src/shared/java")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.systemProperty("yt.live", (project.findProperty("yt.live") ?: "false").toString())
            it.testLogging {
                showStandardStreams = project.findProperty("yt.live") == "true"
            }
        }
    }

    buildFeatures {
        compose = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        baseline = file("lint-baseline.xml")
        htmlReport = true
        textReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

base {
    archivesName = "SoundCloud-Android-$appVersionName"
}

tasks.register("printVersionName") {
    doLast { println(appVersionName) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")
    implementation("androidx.media3:media3-datasource:1.9.0")
    implementation("androidx.media3:media3-database:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("com.google.guava:guava:33.7.2-android")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    "fullImplementation"("dev.chrisbanes.haze:haze:1.6.0")
    "fullImplementation"("androidx.camera:camera-camera2:1.4.2")
    "fullImplementation"("androidx.camera:camera-lifecycle:1.4.2")
    "fullImplementation"("androidx.camera:camera-view:1.4.2")
    "fullImplementation"("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
}
