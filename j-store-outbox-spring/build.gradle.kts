plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.spring.data.jpa)
    api(libs.spring.boot.starter.data.jpa)
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-messaging-core"))
    implementation(project(":j-store-outbox-core"))
    implementation(project(":j-store-messaging-local-spring"))
    implementation(libs.micrometer.core)
    implementation(libs.spring.boot.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.embedded.postgres)
    testImplementation(project(":j-store-order-domain"))
    testImplementation(project(":j-store-integration-contracts"))
    testRuntimeOnly(libs.postgresql)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("capacity")
    }
}

tasks.register<Test>("outboxRelayCapacityTest") {
    description = "Measures committed Outbox relay latency against embedded PostgreSQL."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("capacity")
    }
    filter {
        includeTestsMatching("*OutboxRelayCapacityTest")
    }
    listOf(
            "messageCount",
            "producerConcurrency",
            "batchSize",
            "maxBatchesPerDrain",
            "timeoutSeconds",
            "output",
        )
        .forEach { name ->
            providers.gradleProperty("outboxCapacity.$name").orNull?.let { value ->
                systemProperty("outboxCapacity.$name", value)
            }
        }
    outputs.upToDateWhen { false }
}

kotlin {
    jvmToolchain(25)
}
