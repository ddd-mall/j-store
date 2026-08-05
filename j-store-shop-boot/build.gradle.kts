plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":j-store-shop-domain"))
    implementation(project(":j-store-shop-application"))
    implementation(project(":j-store-shop-infrastructure"))
    implementation(project(":j-store-shop-api"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-user-domain"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
