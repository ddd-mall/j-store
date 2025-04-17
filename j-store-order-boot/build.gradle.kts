plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.springframework)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":j-store-order"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.data.commons)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
    annotationProcessor(libs.spring.boot.configuration.processor)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.postgresql)
    implementation(libs.fastexcel)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)

    implementation(libs.commons.lang3)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}


