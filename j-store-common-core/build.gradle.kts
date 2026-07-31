plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}


dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")

    api(libs.guava)
    api(libs.slf4j.api)
    api(libs.seata.all)

    api(platform(libs.jackson.bom))
    api(libs.jackson.core)
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.module.kotlin)
    api(libs.money.api)

}

tasks.withType<Test> {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}

kotlin {
    jvmToolchain(25)
}
