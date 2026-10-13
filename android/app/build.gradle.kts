plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.rahier.unrolled"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.rahier.unrolled"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing. Upload keystore is NOT committed — expected to
    // live at `$PROJECT/keystore.jks` (gitignored) with passwords in
    // `~/.gradle/gradle.properties` (RELEASE_STORE_PASSWORD,
    // RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD). Falls back to the debug
    // key when any of those are missing so local `./gradlew assembleRelease`
    // still works without ceremony; Play App Signing then re-signs
    // server-side with the real upload key on CI/CD.
    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore.jks")
            val storePw = findProperty("RELEASE_STORE_PASSWORD") as String?
            val keyAliasProp = findProperty("RELEASE_KEY_ALIAS") as String?
            val keyPw = findProperty("RELEASE_KEY_PASSWORD") as String?
            if (keystoreFile.exists() && storePw != null && keyAliasProp != null && keyPw != null) {
                storeFile = keystoreFile
                storePassword = storePw
                keyAlias = keyAliasProp
                keyPassword = keyPw
            }
        }
    }

    buildTypes {
        release {
            // R8 strips unused code + resources. `proguard-rules.pro` only
            // needs overrides on top of library consumer rules (Compose,
            // Coil, AndroidX all ship their own); keep an eye on the
            // pre-launch report for any runtime reflection fallout.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")?.takeIf {
                it.storeFile != null
            } ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Gradle 9.8 deprecates Configuration.setVisible, which AGP 9.4.1 still calls.
        disable += "AndroidGradlePluginVersion"
        warningsAsErrors = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Coil core only. `coil-network-okhttp` (HTTP loader + OkHttp) is
    // NOT included — we exclusively load `content://` URIs resolved by
    // Coil's built-in ContentResolver fetcher, and shipping OkHttp
    // would both bloat the APK and flag networking capability to Play's
    // Data Safety reviewers despite our "no data transmitted" claim.
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-video:3.6.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("app.cash.turbine:turbine:1.2.1")
}
