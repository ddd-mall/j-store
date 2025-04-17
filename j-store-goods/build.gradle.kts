plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.kotlin.plugin.jpa)
    alias(libs.plugins.springframework)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
    implementation(platform(libs.spring.boot.dependencies))
    api(libs.spring.data.jpa)
    api(libs.spirng.boot.boot)
    testImplementation(libs.spring.boot.starter.test)
    implementation(libs.seata.all)
    api(project(":j-store-common"))
    api(project(":j-store-common-spring"))

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}