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
    testImplementation(kotlin("test"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(project(":j-store-common"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}