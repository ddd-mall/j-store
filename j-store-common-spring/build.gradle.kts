plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":j-store-common-core"))
    implementation(libs.fastexcel)
    implementation("org.springframework:spring-context")

    // Test dependencies
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
