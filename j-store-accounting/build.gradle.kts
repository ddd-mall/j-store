plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.jstore"

version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlin.reflect)
    api(project(":j-store-common-core"))
    implementation(project(":j-store-order"))
    implementation(project(":j-store-payment"))
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
