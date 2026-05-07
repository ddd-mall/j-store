plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(platform(libs.spring.boot.dependencies))
    api(libs.spring.data.jpa)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.seata.all)
    implementation(project(":j-store-common-core"))
    implementation(libs.fastexcel)
    implementation("io.micrometer:micrometer-core")

    // Spring Boot autoconfigure (for @ConditionalOnProperty, @EnableScheduling, etc.)
    implementation(libs.spirng.boot.boot)

    // Test dependencies
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation(project(":j-store-order"))
    testRuntimeOnly(libs.postgresql)
}

tasks.test {
    useJUnitPlatform()
    environment("DOCKER_API_VERSION", "1.44")
    environment("api.version", "1.44")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    System.getenv("DOCKER_HOST")?.let { environment("DOCKER_HOST", it) }
    systemProperty("docker.api.version", "1.44")
    systemProperty("api.version", "1.44")
}
kotlin {
    jvmToolchain(21)
}
