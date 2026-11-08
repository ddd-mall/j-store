plugins { alias(libs.plugins.kotlin.jvm) }

repositories { mavenCentral() }

dependencies { api(libs.kotlin.stdlib) }

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(25) }
