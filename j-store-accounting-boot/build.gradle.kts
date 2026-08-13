plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.spring.context)
    implementation(project(":j-store-accounting-domain"))
    implementation(project(":j-store-accounting-application"))
    implementation(project(":j-store-accounting-infrastructure"))
    implementation(project(":j-store-order-domain"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-integration-contracts"))
    implementation(libs.spring.tx)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
