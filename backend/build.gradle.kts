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
    implementation("org.springframework.boot:spring-boot-starter-validation")

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

tasks.compileJava {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.test {
    useJUnitPlatform()
}
