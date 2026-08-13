plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.spring.context)
    implementation(project(":j-store-warehouse-domain"))
    implementation(project(":j-store-warehouse-application"))
    implementation(project(":j-store-warehouse-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(libs.spring.tx)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
