import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version is owned by build_number.txt (bumped by 10-MakeRelease.sh). Never hand-edit here.
val buildNumberFile = rootProject.file("build_number.txt")
val buildProps = Properties()
if (buildNumberFile.exists()) {
    buildNumberFile.inputStream().use { buildProps.load(it) }
}
val releaseVersionName = buildProps.getProperty("version") ?: "0.3.20260702"
val releaseVersionCode = buildProps.getProperty("build")?.trim()?.toIntOrNull() ?: 1

// Release signing reads /home/e/.my-safe/key.properties (falls back to repo key.properties). Unsigned if absent.
val keyProperties = Properties()
val keyPropertiesFile = sequenceOf(
    File("/home/e/.my-safe/key.properties"),
    rootProject.file("key.properties"),
).firstOrNull { it.exists() } ?: rootProject.file("key.properties")
val hasReleaseSigning = keyPropertiesFile.exists().also { exists ->
    if (exists) {
        keyPropertiesFile.inputStream().use { keyProperties.load(it) }
    }
}
val keyStoreFile = (keyProperties["storeFile"] as String?)?.let { rawPath ->
    val candidate = File(rawPath)
    if (candidate.isAbsolute) candidate else File(keyPropertiesFile.parentFile, rawPath)
}

android {
    namespace = "com.reminderlists"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.reminderlists"
        minSdk = 33
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        // Room schema export for migration diffs / tests (TZ 6.4).
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keyStoreFile
                storePassword = keyProperties["storePassword"] as String
                keyAlias = keyProperties["keyAlias"] as String
                keyPassword = keyProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Lint runs on demand through 05-Lint.sh, not inside every release build: lintVitalAnalyze
    // costs ~26 s of a ~48 s assembleRelease and repeats what the script already reports.
    lint {
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

abstract class RenameReleaseApks : DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val versionName: Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val versionCode: Property<Int>

    @get:org.gradle.api.tasks.Internal
    abstract val outputDir: DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun rename() {
        val outDir = outputDir.get().asFile
        val prefix = "reminderlists-${versionName.get()}+${versionCode.get()}-release"
        val mappings = mapOf(
            "app-universal-release.apk" to "$prefix-universal.apk",
            "app-arm64-v8a-release.apk" to "$prefix-arm64-v8a.apk",
            "app-armeabi-v7a-release.apk" to "$prefix-armeabi-v7a.apk",
            "app-x86_64-release.apk" to "$prefix-x86_64.apk",
        )
        mappings.forEach { (srcName, dstName) ->
            val src = File(outDir, srcName)
            if (!src.exists()) return@forEach
            val dst = File(outDir, dstName)
            if (dst.exists()) dst.delete()
            src.renameTo(dst)
        }
    }
}

val renameReleaseApks by tasks.registering(RenameReleaseApks::class) {
    versionName.set(releaseVersionName)
    versionCode.set(releaseVersionCode)
    outputDir.set(layout.buildDirectory.dir("outputs/apk/release"))
}

tasks.configureEach {
    if (name == "assembleRelease") {
        finalizedBy(renameReleaseApks)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
}
