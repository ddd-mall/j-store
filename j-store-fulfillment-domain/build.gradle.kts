plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.stdlib)
    api(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
