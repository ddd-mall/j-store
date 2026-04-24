plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(platform(libs.spring.boot.dependencies))
    api(libs.spring.data.jpa)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.data.jpa)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.seata.all)
    implementation(project(":j-store-common-core"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}