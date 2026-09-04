plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(project(":j-store-cart-api"))
    implementation(project(":j-store-cart-domain"))
    implementation(project(":j-store-cart-application"))
    implementation(project(":j-store-cart-infrastructure"))
    implementation(project(":j-store-goods-api"))
    implementation(project(":j-store-shop-api"))
    implementation(project(":j-store-inventory-api"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(project(":j-store-common-core"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.tx)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
