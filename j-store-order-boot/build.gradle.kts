plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":j-store-order-domain"))
    implementation(project(":j-store-order-application"))
    implementation(project(":j-store-order-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-goods-api"))
    implementation(project(":j-store-goods-application"))
    implementation(project(":j-store-user-domain"))
    implementation(project(":j-store-shop"))
    implementation(project(":j-store-authentication-spring-sdk"))

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.redis)
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
