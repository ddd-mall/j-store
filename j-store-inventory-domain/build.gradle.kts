plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(libs.kotlin.stdlib)
    api(project(":j-store-common-core"))
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
