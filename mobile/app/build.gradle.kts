plugins {
    id("org.openapi.generator") version "7.26.0"
    id("com.android.application") version "9.3.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("com.google.devtools.ksp") version "2.3.12"
}

android {
    namespace = "com.playly.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.playly.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            val endpoint = providers.gradleProperty("playlyCoreUrl").orElse("http://10.0.2.2:8080/api/v1")
            buildConfigField("String", "CORE_URL", "\"${endpoint.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        release {
            val endpoint = providers.gradleProperty("playlyReleaseCoreUrl").orElse("")
            buildConfigField("String", "CORE_URL", "\"${endpoint.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.google.dagger:dagger:2.60.1")
    ksp("com.google.dagger:dagger-compiler:2.60.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.moshi:moshi-adapters:1.15.2")
    testImplementation("junit:junit:4.13.2")
}

val generatedClient = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    inputSpec.set(rootProject.file("../OpenApi.yml").absolutePath)
    outputDir.set(generatedClient)
    cleanupOutput.set(true)
    configOptions.set(mapOf(
        "packageName" to "com.playly.app.generated",
        "serializationLibrary" to "moshi",
        "omitGradleWrapper" to "true"
    ))
    generateApiTests.set(false)
    generateModelTests.set(false)
}

android.sourceSets.named("main") {
    kotlin.directories += generatedClient.get().dir("src/main/kotlin").asFile.absolutePath
}

tasks.named("preBuild") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn(tasks.named("openApiGenerate"))
}
