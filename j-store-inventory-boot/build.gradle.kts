plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.spring.context)
    implementation(project(":j-store-inventory-domain"))
    implementation(project(":j-store-inventory-application"))
    implementation(project(":j-store-inventory-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-messaging-core"))
    implementation(project(":j-store-integration-contracts"))
    implementation(libs.spring.tx)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
