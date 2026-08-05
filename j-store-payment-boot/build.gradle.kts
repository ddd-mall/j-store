plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.plugin.spring) }
repositories { mavenCentral() }
dependencies {
    implementation(project(":j-store-payment-domain"))
    implementation(project(":j-store-payment-application"))
    implementation(project(":j-store-payment-infrastructure"))
    implementation(project(":j-store-common-core"))
    implementation(project(":j-store-common-spring"))
    implementation(project(":j-store-integration-contracts"))
    implementation(project(":j-store-shop"))
    implementation(project(":j-store-authentication-spring-sdk"))
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(25) }
