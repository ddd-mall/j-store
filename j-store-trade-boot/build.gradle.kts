plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.spring.context)
    implementation(project(":j-store-trade-domain"))
    implementation(project(":j-store-trade-application"))
    implementation(project(":j-store-trade-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-messaging-core"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(project(":j-store-user-domain"))
    implementation(project(":j-store-shop-api"))
    implementation(project(":j-store-goods-api"))
    implementation(project(":j-store-user-api"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.tx)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
