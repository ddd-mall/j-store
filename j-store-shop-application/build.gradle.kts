plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(libs.kotlin.stdlib)
    api(project(":j-store-shop-domain"))
    implementation(project(":j-store-shop-api"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-common-core"))
    api(project(":j-store-messaging-core"))
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
