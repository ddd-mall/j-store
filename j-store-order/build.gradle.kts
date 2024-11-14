plugins {
    kotlin("jvm") version "1.9.25"
}

group = "com.jstore"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":j-store-common"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}