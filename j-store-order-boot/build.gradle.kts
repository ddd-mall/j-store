plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.springframework) apply(true)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":j-store-common"))
    implementation(project(":j-store-order"))
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.data.commons)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
    annotationProcessor(libs.spring.boot.configuration.processor)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.postgresql)
    implementation(libs.fastexcel)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}