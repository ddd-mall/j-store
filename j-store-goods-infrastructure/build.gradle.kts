plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    api(project(":j-store-goods"))

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.data.commons)
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)

    runtimeOnly(libs.postgresql)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(libs.commons.lang3)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

