plugins {
    id("org.openapi.generator") version "7.26.0"
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.moshi:moshi-adapters:1.15.2")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}

val generatedClient = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    inputSpec.set(rootProject.file("../OpenApi.yml").absolutePath)
    outputDir.set(generatedClient.map { it.asFile.absolutePath })
    cleanupOutput.set(true)
    configOptions.set(mapOf(
        "packageName" to "com.playly.app.generated",
        "serializationLibrary" to "moshi",
        "omitGradleWrapper" to "true"
    ))
    generateApiTests.set(false)
    generateModelTests.set(false)
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generatedClient.map { it.dir("src/main/kotlin") })
}

tasks.named("preBuild") {
    dependsOn(tasks.named("openApiGenerate"))
}
