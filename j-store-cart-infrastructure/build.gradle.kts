plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.kotlin.stdlib)
    api(project(":j-store-cart-domain"))
    implementation(project(":j-store-goods-api"))
    implementation(project(":j-store-shop-api"))
    implementation(project(":j-store-inventory-api"))
    implementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.embedded.postgres)
    testRuntimeOnly(libs.postgresql)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
