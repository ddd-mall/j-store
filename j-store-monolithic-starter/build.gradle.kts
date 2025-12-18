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
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    
    // Spring Boot starters
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.devtools)
    testImplementation(libs.spring.boot.starter.test)
    
    // Database
    runtimeOnly(libs.postgresql)
    
    // Project modules
    implementation(project(":j-store-common"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-order"))
    implementation(project(":j-store-order-infrastructure"))
    implementation(project(":j-store-goods"))
    implementation(project(":j-store-goods-infrastructure"))
    
    // Utils
    implementation(libs.commons.lang3)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

