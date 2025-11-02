plugins {
    id("java")
}

group = "com.jstore.order"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.redisson.spring.boot.starter)
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}