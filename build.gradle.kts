plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.spring") version "2.1.21"
}

allprojects {
    group = property("projectGroup") as String
    version = property("projectVersion") as String
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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