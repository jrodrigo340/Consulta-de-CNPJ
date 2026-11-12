plugins {
    java
    id("org.springframework.boot") version "4.0.7"
}

group = "br.com.jae"
version = "0.1.0-SNAPSHOT"
description = "Transmissor fiscal: consome eventos DOCUMENTO_CALCULADO, transmite à SEFAZ e publica webhooks de status (serviço isolado do ecossistema Já É)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.7"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
