plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.springframework)
}

group = "com.jstore"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":j-store-order"))
    implementation(project(":j-store-order-infrastructure"))
    implementation(project(":j-store-goods"))


    implementation(platform(libs.spring.cloud.dependencies))
    implementation(libs.spring.cloud.loadbalancer)

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.data.commons)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.starter.test)
    annotationProcessor(libs.spring.boot.configuration.processor)
//    developmentOnly(libs.spring.boot.devtools)

    implementation(platform(libs.spring.cloud.alibaba.dependencies))
    implementation(libs.spring.cloud.starter.alibaba.nacos.discovery)
    implementation(libs.spring.cloud.starter.alibaba.nacos.config)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}