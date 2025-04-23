plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.springframework)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.stdlib)
    api(libs.kotlin.reflect)
    api(libs.spring.data.jpa)
    implementation(platform(libs.spring.boot.dependencies))
    api(libs.spirng.boot.boot)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito)
    api(project(":j-store-common"))
    api(project(":j-store-common-spring"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}