plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.springframework)
    kotlin("plugin.lombok")
    kotlin("kapt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(project(":j-store-order-domain"))
    implementation(project(":j-store-order-boot"))
    implementation(project(":j-store-payment-domain"))
    implementation(project(":j-store-payment-boot"))
    implementation(project(":j-store-fulfillment-domain"))
    implementation(project(":j-store-fulfillment-boot"))
    implementation(project(":j-store-goods-domain"))
    implementation(project(":j-store-goods-boot"))
    implementation(project(":j-store-inventory-domain"))
    implementation(project(":j-store-inventory-boot"))
    implementation(project(":j-store-warehouse-domain"))
    implementation(project(":j-store-warehouse-boot"))
    implementation(project(":j-store-user-domain"))
    implementation(project(":j-store-user-boot"))
    implementation(project(":j-store-shop-boot"))
    implementation(project(":j-store-shop-domain"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-messaging-core"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-messaging-local-spring"))
    implementation(project(":j-store-outbox-core"))
    implementation(project(":j-store-outbox-spring"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(project(":j-store-accounting-boot"))

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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.flyway.core)
    testImplementation(libs.spring.boot.starter.test)
    annotationProcessor(libs.spring.boot.configuration.processor)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation("io.zonky.test:embedded-postgres:2.1.0")
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(libs.commons.lang3)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    implementation(libs.fastexcel)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kapt {
    keepJavacAnnotationProcessors = true
}
