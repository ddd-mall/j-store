plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(project(":j-store-inventory-domain"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-integration-contracts"))
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
