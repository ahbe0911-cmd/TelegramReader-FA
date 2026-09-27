import java.io.File
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.telegramreader.fa"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.telegramreader.fa"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "0.7.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val vazirmatnFontDir = layout.projectDirectory.dir("src/main/res/font")
val downloadVazirmatnFonts by tasks.registering {
    val files = mapOf(
        "vazirmatn_regular.ttf" to "https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/ttf/Vazirmatn-Regular.ttf",
        "vazirmatn_medium.ttf" to "https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/ttf/Vazirmatn-Medium.ttf",
        "vazirmatn_semibold.ttf" to "https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/ttf/Vazirmatn-SemiBold.ttf",
        "vazirmatn_bold.ttf" to "https://raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/ttf/Vazirmatn-Bold.ttf"
    )

    outputs.files(files.keys.map { vazirmatnFontDir.file(it).asFile })

    doLast {
        vazirmatnFontDir.asFile.mkdirs()
        files.forEach { (name, source) ->
            val target = vazirmatnFontDir.file(name).asFile
            if (!target.exists() || target.length() < 50_000L) {
                logger.lifecycle("Downloading Vazirmatn font: $name")
                val temp = File(target.parentFile, "$name.tmp")
                URI(source).toURL().openStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists()) target.delete()
                check(temp.renameTo(target)) { "Could not install $name" }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadVazirmatnFonts)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-beta01")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    // Dedicated Pdfium-based viewer: zoom, smooth scrolling and page caching.
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
