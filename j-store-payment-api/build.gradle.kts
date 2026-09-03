plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies { api(libs.kotlin.stdlib) }

kotlin { jvmToolchain(25) }
