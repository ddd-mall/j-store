plugins {
    alias(libs.plugins.jvm)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"



repositories {
    mavenCentral()
}


dependencies {
    implementation(libs.spring.data.jpa)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.guava)
    implementation(libs.slf4j.api)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
