plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":j-store-fulfillment-domain"))
    implementation(project(":j-store-common-core"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.data.jpa)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.embedded.postgres)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
