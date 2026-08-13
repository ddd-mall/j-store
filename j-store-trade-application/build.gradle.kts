plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(project(":j-store-trade-domain"))
    implementation(project(":j-store-common-core"))
    api(project(":j-store-messaging-core"))
    implementation(project(":j-store-integration-contracts"))
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
