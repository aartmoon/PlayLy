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
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.62.0"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
