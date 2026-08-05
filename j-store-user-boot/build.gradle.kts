plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.plugin.spring) }
repositories { mavenCentral() }
dependencies {
    implementation(project(":j-store-user-domain"))
    implementation(project(":j-store-user-application"))
    implementation(project(":j-store-user-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.redis)
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(25) }
