plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies {
    api(libs.kotlin.stdlib)
    api(project(":j-store-common-core"))
}

kotlin { jvmToolchain(25) }
