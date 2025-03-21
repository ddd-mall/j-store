plugins {
	alias(libs.plugins.jvm)
	alias(libs.plugins.kotlin.plugin.spring)
	alias(libs.plugins.springframework)
}

group = "com.jstore"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(platform(libs.spring.cloud.dependencies))
	implementation(platform(libs.spring.boot.dependencies))
	implementation(platform(libs.spring.modulith.bom))

	implementation(libs.kotlin.reflect)
	implementation(libs.spring.cloud.function.context)
	implementation(libs.spring.cloud.starter)
	implementation(libs.spring.cloud.starter.netflix.eureka.client)
	implementation(libs.spring.cloud.starter.netflix.eureka.server)
	implementation(libs.spring.cloud.starter.task)
	implementation(libs.spring.cloud.starter.zookeeper.discovery)
	implementation(libs.spring.modulith.starter.core)
	annotationProcessor(libs.spring.boot.configuration.processor)
	testImplementation(libs.spring.boot.starter.test)
	testImplementation(libs.kotlin.test.junit5)
	testImplementation(libs.spring.modulith.starter.test)
	testRuntimeOnly(libs.junit.platform.launcher)
}


kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.register("prepareKotlinBuildScriptModel"){}