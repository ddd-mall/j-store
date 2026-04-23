plugins {
    alias(libs.plugins.kotlin.jvm)
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