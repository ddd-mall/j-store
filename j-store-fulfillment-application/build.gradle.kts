plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(libs.kotlin.stdlib)
    api(project(":j-store-fulfillment-domain"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-messaging-core"))
    implementation(project(":j-store-integration-contracts"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
