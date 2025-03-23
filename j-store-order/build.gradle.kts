plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.springframework)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.spring.data.jpa)
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spirng.boot.boot)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito)
    implementation(project(":j-store-common"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}