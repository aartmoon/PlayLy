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
    implementation("org.apache.kafka:kafka-clients:4.2.2")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
    implementation(platform("software.amazon.awssdk:bom:2.39.6"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")
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
    description = "Generate, build and smoke-test a Phaser game."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.playly.ai.plan.GameGenerationCli")
}

tasks.register<JavaExec>("serveRunner") {
    group = "application"
    description = "Serve isolated builds on a trusted Docker host with gVisor or Kata."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.playly.ai.plan.RunnerServer")
}

tasks.processResources {
    from("../game-template/src") {
        include("*.ts")
        into("game/examples")
    }
}

tasks.test {
    useJUnitPlatform()
}
