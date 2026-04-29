plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.springframework)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":j-store-order"))
    implementation(project(":j-store-order-infrastructure"))
    implementation(project(":j-store-goods"))
    implementation(project(":j-store-goods-infrastructure"))
    implementation(project(":j-store-user"))
    implementation(project(":j-store-user-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-authentication-spring-sdk"))

//    implementation(platform(libs.spring.cloud.dependencies))
//    implementation(libs.spring.cloud.loadbalancer)

//    implementation(platform(libs.spring.cloud.alibaba.dependencies))
//    implementation(libs.spring.cloud.starter.alibaba.nacos.discovery)
//    implementation(libs.spring.cloud.starter.alibaba.nacos.config)

    implementation(libs.spring.data.redis)
    implementation(libs.spring.boot.starter.data.redis)

    implementation(platform(libs.spring.boot.dependencies))
    annotationProcessor(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.data.commons)

    implementation(libs.spring.data.jpa)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.starter.test)
    annotationProcessor(libs.spring.boot.configuration.processor)

    runtimeOnly(libs.postgresql)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(libs.commons.lang3)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    implementation(libs.fastexcel)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}


