plugins {
    java
    id("org.openapi.generator") version "7.26.0"
    id("org.springframework.boot") version "4.1.2"
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("io.minio:minio:8.6.0")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    constraints {
        val bouncyCastleVersion = "1.86"
        implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion") {
            because("MinIO requests an affected release (CVE-2026-8763, CVE-2026-59650, CVE-2026-58062, CVE-2026-58059)")
        }
        implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion") {
            because("Keep Bouncy Castle modules aligned with bcprov")
        }
        implementation("org.bouncycastle:bcutil-jdk18on:$bouncyCastleVersion") {
            because("Keep Bouncy Castle modules aligned with bcprov")
        }
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val generatedApi = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(rootProject.file("../OpenApi.yml").absolutePath)
    outputDir.set(generatedApi)
    apiPackage.set("com.playly.core.generated.api")
    modelPackage.set("com.playly.core.generated.model")
    cleanupOutput.set(true)
    configOptions.set(mapOf(
        "useSpringBoot4" to "true",
        "interfaceOnly" to "true",
        "useTags" to "true",
        "annotationLibrary" to "none",
        "documentationProvider" to "none",
        "openApiNullable" to "false",
        "useOneOfInterfaces" to "false"
    ))
    globalProperties.set(mapOf(
        "apis" to "",
        "models" to "",
        "supportingFiles" to "ApiUtil.java"
    ))
}

sourceSets.main {
    java.srcDir(generatedApi.map { it.dir("src/main/java") })
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.compileJava {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.test {
    useJUnitPlatform()
}
