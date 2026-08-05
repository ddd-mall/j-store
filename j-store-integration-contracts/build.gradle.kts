plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":j-store-common-core"))
    implementation(libs.kotlin.stdlib)
}

kotlin {
    jvmToolchain(25)
}
