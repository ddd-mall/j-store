plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.spring.context)
    implementation(project(":j-store-payment-domain"))
    implementation(project(":j-store-payment-application"))
    implementation(project(":j-store-payment-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-trade-application"))
    implementation(project(":j-store-shop-application"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.tx)
    implementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.jdbc)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
