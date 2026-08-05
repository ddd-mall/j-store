plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":j-store-warehouse-domain"))
    implementation(project(":j-store-warehouse-application"))
    implementation(project(":j-store-warehouse-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-common-spring"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation("org.springframework:spring-tx")
    testImplementation(libs.spring.boot.starter.test)
}

tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(25) }
