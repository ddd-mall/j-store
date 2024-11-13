plugins {
    kotlin("jvm") version "1.9.25"
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"



repositories {
    mavenCentral()
}


dependencies {
    implementation(libs.guava)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
