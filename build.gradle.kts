plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.lombok) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.plugin.spring)
    id("org.cyclonedx.bom") version "3.3.0"
}

allprojects {
    group = property("projectGroup") as String
    version = property("projectVersion") as String
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven {
        setUrl("https://maven.aliyun.com/repository/public")
    }
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib"))
}

spotless {
    ratchetFrom("origin/master")

    java {
        target("**/src/**/*.java")
        targetExclude("**/build/**", "**/bin/**")
        googleJavaFormat("1.35.0").aosp()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/bin/**")
        ktfmt("0.63").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**", "**/bin/**")
        ktfmt("0.63").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    tasks.cyclonedxDirectBom {
        includeConfigs = listOf("runtimeClasspath")
    }
}
