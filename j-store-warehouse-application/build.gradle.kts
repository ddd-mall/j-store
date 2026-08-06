plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(project(":j-store-warehouse-domain"))
    implementation(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
