plugins {
    application
    id("org.jsonschema2pojo") version "1.3.3"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

application {
    mainClass.set("com.playly.ai.GenerationWorker")
}

repositories {
    mavenCentral()
}

jsonSchema2Pojo {
    setSource(files("src/main/schema"))
    targetDirectory = layout.buildDirectory.dir("generated/kafka").get().asFile
    targetPackage = "com.playly.ai.generated.kafka"
    setAnnotationStyle("none")
    dateTimeType = "java.lang.String"
    removeOldOutput = true
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.62.0"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<JavaExec>("planGame") {
    group = "application"
    description = "Request and validate a game plan from the configured LLM."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.playly.ai.plan.GamePlanCli")
}

tasks.register<JavaExec>("generateGame") {
    group = "application"
    description = "Generate and validate TypeScript source files for a game plan."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.playly.ai.plan.GameGenerationCli")
}

tasks.test {
    useJUnitPlatform()
}
