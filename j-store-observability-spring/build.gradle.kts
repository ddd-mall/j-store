plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.spring.boot.starter.actuator)
    api(libs.micrometer.tracing.bridge.otel)
    runtimeOnly(libs.micrometer.registry.prometheus)

    compileOnly(libs.spring.boot.starter.web)

    testImplementation(kotlin("test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}
