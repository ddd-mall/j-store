plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlin.reflect)
    testImplementation(libs.mockito)
    api(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
