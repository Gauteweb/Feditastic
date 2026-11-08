import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** Lokale innstillinger (git-ignorert), f.eks. signReleaseWithDebugKey=true for egne bygg. */
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    // Kotlin-pakken heter fortsatt app.pleromastisk; appens ID (applicationId) er app.feditastic
    namespace = "app.pleromastisk"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.feditastic"
        minSdk = 26
        targetSdk = 35
        versionCode = 31
        versionName = "2.4.0"
        // Varsel om ny utgivelse på GitHub: bare i egne bygg (githubUpdateCheck=true i local.properties),
        // ikke i F-Droid-bygg, der F-Droid står for oppdateringene.
        buildConfigField(
            "boolean", "UPDATE_CHECK",
            (localProps.getProperty("githubUpdateCheck") == "true").toString(),
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Usignert som standard: F-Droid bygger og signerer selv. Med signReleaseWithDebugKey=true
            // i local.properties signeres den med debug-nøkkelen, så den kan installeres rett på mobilen.
            if (localProps.getProperty("signReleaseWithDebugKey") == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

/**
 * Norsk tekst finnes bare i values-nb. Nynorsk (nn) og eldre enheter som oppgir «no» faller ikke
 * tilbake til nb av seg selv, men til standardspråket (engelsk). Derfor kopieres den norske filen
 * til values-nn og values-no ved hver bygging.
 */
abstract class NorwegianStrings : DefaultTask() {
    @get:InputFile
    abstract val bokmal: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        for (folder in listOf("values-nn", "values-no")) {
            val target = outputDir.file("$folder/strings.xml").get().asFile
            target.parentFile.mkdirs()
            bokmal.get().asFile.copyTo(target, overwrite = true)
        }
    }
}

val norwegianStrings = tasks.register<NorwegianStrings>("norwegianStrings") {
    bokmal.set(layout.projectDirectory.file("src/main/res/values-nb/strings.xml"))
    outputDir.set(layout.buildDirectory.dir("generated/res/norwegian"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(norwegianStrings, NorwegianStrings::outputDir)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
