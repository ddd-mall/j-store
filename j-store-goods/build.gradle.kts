plugins {
    kotlin("jvm") version "1.9.20"
}

group = "com.jstore"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.spring.data.jpa)
    implementation(libs.spirng.boot.boot)
    testImplementation(libs.spring.boot.starter.test)
    implementation(project(":j-store-common"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}