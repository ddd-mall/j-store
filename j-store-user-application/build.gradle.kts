plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(libs.kotlin.stdlib)
    api(project(":j-store-user-domain"))
    implementation(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockito)
    testImplementation(libs.mockito.kotlin)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
