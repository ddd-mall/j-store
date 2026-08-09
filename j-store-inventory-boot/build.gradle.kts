plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":j-store-inventory-domain"))
    implementation(project(":j-store-inventory-application"))
    implementation(project(":j-store-inventory-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-integration-contracts"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation("org.springframework:spring-tx")
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
