plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pt.cpwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.cpwidget"
        minSdk = 31 // Android 12
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    signingConfigs {
        // Fixed, non-secret signing key committed to the repo so every CI build
        // can be installed as an update over the previous one (sideload only).
        create("sideload") {
            storeFile = file("sideload.keystore")
            storePassword = "cpwidget"
            keyAlias = "cpwidget"
            keyPassword = "cpwidget"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Real org.json for JVM unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    systemProperty("cp.live", System.getProperty("cp.live") ?: "false")
    testLogging { showStandardStreams = true; events("passed", "failed", "skipped") }
}
