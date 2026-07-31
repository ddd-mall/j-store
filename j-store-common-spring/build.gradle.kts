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
    api(libs.spring.data.jpa)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.seata.all)
    implementation(project(":j-store-common-core"))
    implementation(libs.fastexcel)
    implementation("io.micrometer:micrometer-core")

    // Spring Boot autoconfigure (for @ConditionalOnProperty, @EnableScheduling, etc.)
    implementation(libs.spirng.boot.boot)

    // Test dependencies
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testImplementation(project(":j-store-order"))
    testRuntimeOnly(libs.postgresql)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(25)
}
