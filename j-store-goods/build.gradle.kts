plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.springframework) apply(true)

}

group = "com.jstore"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.spring.boot.dependencies))
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