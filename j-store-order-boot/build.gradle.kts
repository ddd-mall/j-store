plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.springframework) apply(false)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":j-store-common"))
    implementation(project(":j-store-order"))
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.data.commons)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.configuration.processor)
    implementation(libs.postgresql)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}