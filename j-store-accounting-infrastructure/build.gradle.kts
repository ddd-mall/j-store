plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    api(project(":j-store-accounting"))
    implementation(project(":j-store-common-core"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.data.jpa)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testRuntimeOnly("com.h2database:h2")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
