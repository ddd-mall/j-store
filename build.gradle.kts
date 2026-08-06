plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spotless)
    kotlin("plugin.spring") version "2.3.0"
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

val prePushTargetFile = providers.gradleProperty("spotlessFilesFile").orNull
val prePushTargets = prePushTargetFile?.let { path ->
    val targetList = rootProject.file(path)
    require(targetList.isFile) { "Spotless target list does not exist: $targetList" }
    targetList.readLines().filter(String::isNotBlank).map(rootProject::file).filter(File::isFile)
}

val javaSourceTrees = allprojects.map { candidate ->
    candidate.fileTree("src") {
        include("**/*.java")
        exclude("**/build/**", "**/bin/**")
    }
}
val kotlinSourceTrees = allprojects.map { candidate ->
    candidate.fileTree("src") {
        include("**/*.kt")
        exclude("**/build/**", "**/bin/**")
    }
}
val kotlinGradleFiles =
    files(
        allprojects.map(Project::getBuildFile).filter {
            it.isFile && it.name.endsWith(".gradle.kts")
        },
        rootProject.file("settings.gradle.kts"),
    )

spotless {
    if (prePushTargets == null) {
        ratchetFrom("origin/master")
    }

    java {
        target(prePushTargets?.filter { it.extension == "java" } ?: javaSourceTrees)
        googleJavaFormat("1.35.0").aosp()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlin {
        target(prePushTargets?.filter { it.extension == "kt" } ?: kotlinSourceTrees)
        ktfmt("0.63").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target(prePushTargets?.filter { it.name.endsWith(".gradle.kts") } ?: kotlinGradleFiles)
        ktfmt("0.63").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val installIncrementalSpotlessPrePushHook by
    tasks.registering(Copy::class) {
        group = "Spotless"
        description = "Installs the repository's incremental Spotless pre-push hook."
        from(layout.projectDirectory.file("scripts/git-hooks/pre-push"))
        into(layout.projectDirectory.dir(".git/hooks"))
        doLast {
            layout.projectDirectory.file(".git/hooks/pre-push").asFile.setExecutable(true)
        }
    }

tasks.named("spotlessInstallGitPrePushHook") {
    finalizedBy(installIncrementalSpotlessPrePushHook)
}

allprojects {
    tasks.cyclonedxDirectBom {
        includeConfigs = listOf("runtimeClasspath")
    }
}
