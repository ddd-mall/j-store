plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.context)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":j-store-order-domain"))
    implementation(project(":j-store-order-application"))
    implementation(project(":j-store-order-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-messaging-core"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-trade-application"))
    implementation(project(":j-store-goods-api"))
    implementation(project(":j-store-shop-api"))
    implementation(project(":j-store-user-domain"))
    implementation(project(":j-store-user-client-spring"))
    implementation(project(":j-store-shop-application"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.tx)
    implementation(libs.spring.boot.starter.validation)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.jdbc)
    testImplementation(libs.embedded.postgres)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
