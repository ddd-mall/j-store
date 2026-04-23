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
    testImplementation(libs.mockito)
    api(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}