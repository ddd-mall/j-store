import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import java.util.zip.ZipFile
import org.gradle.api.tasks.bundling.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.licensee) apply false
    alias(libs.plugins.cyclonedx)
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

val projectLicenseFile = rootProject.layout.projectDirectory.file("LICENSE")
val thirdPartyNoticeFile = rootProject.layout.projectDirectory.file("THIRD_PARTY.md")

allprojects {
    tasks.withType<Jar>().configureEach {
        from(projectLicenseFile) {
            into("META-INF")
            rename { "LICENSE" }
        }
        from(thirdPartyNoticeFile) {
            into("META-INF")
            rename { "THIRD_PARTY.md" }
        }
    }
}

subprojects {
    if (path == ":j-store-dependencies-platform") return@subprojects

    apply(plugin = "app.cash.licensee")
    pluginManager.withPlugin("java") {
        dependencies {
            add("implementation", platform(project(":j-store-dependencies-platform")))
            add("testImplementation", platform(project(":j-store-dependencies-platform")))
        }
    }
    pluginManager.withPlugin("java-test-fixtures") {
        dependencies {
            add(
                "testFixturesImplementation",
                platform(project(":j-store-dependencies-platform")),
            )
        }
    }
    extensions.configure<LicenseeExtension> {
        allow("Apache-2.0")
        allow("BSD-2-Clause")
        allow("BSD-3-Clause")
        allow("CC0-1.0")
        allow("EPL-2.0")
        allow("GPL-2.0-with-classpath-exception")
        allow("LGPL-2.1-only")
        allow("MIT")
        allow("MIT-0")
        allow("PostgreSQL")
        allowUrl("http://www.eclipse.org/org/documents/edl-v10.php") {
            because("Eclipse Distribution License 1.0, a BSD-style redistribution license.")
        }
        allowUrl("http://www.eclipse.org/legal/epl-2.0") {
            because("Canonical Eclipse Public License 2.0 URL.")
        }
        allowUrl("https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt") {
            because("Canonical Eclipse Public License 2.0 text URL.")
        }
        allowUrl("https://www.antlr.org/license.html") {
            because("ANTLR 4 is distributed under the BSD 3-Clause license.")
        }
        allowUrl("https://repository.jboss.org/licenses/apache-2.0.txt") {
            because("JBoss-hosted copy of Apache License 2.0.")
        }
        allowUrl("https://jdbc.postgresql.org/about/license.html") {
            because("Canonical PostgreSQL JDBC license URL.")
        }
        allowUrl("https://flywaydb.org/licenses/flyway-oss") {
            because("Flyway OSS identifies this URL as Apache License 2.0 in its POM.")
        }
        allowUrl("https://github.com/redis/lettuce/blob/main/LICENSE") {
            because("Lettuce identifies this URL as the MIT license in its POM.")
        }
        allowUrl("https://github.com/redis/redis-authx-core/blob/master/LICENSE") {
            because("Redis AuthX identifies this URL as the MIT license in its POM.")
        }
        allowUrl("https://opensource.org/license/mit") {
            because("Open Source Initiative MIT license URL.")
        }
        allowDependency("javax.money", "money-api", "1.1") {
            because("The POM names Apache License 2.0 but uses the relative URL LICENSE.txt.")
        }
        allowDependency("aopalliance", "aopalliance", "1.0") {
            because("The published POM declares this AOP Alliance API artifact as Public Domain.")
        }
        unusedAction(UnusedAction.IGNORE)
    }
}

tasks.register("verifyDependencyResolution") {
    group = "verification"
    description = "Verifies approved dependency families resolve consistently at runtime."

    doLast {
        val approvedLog4jVersion = libs.versions.log4j.get()
        val resolvedLog4j = subprojects.flatMap { candidate ->
            val runtimeClasspath = candidate.configurations.findByName("runtimeClasspath")
            if (runtimeClasspath == null || !runtimeClasspath.isCanBeResolved) {
                emptyList()
            } else {
                runtimeClasspath.incoming.resolutionResult.allComponents.mapNotNull { component ->
                    component.moduleVersion
                        ?.takeIf { it.group == "org.apache.logging.log4j" }
                        ?.let { "${candidate.path}\t${it.name}\t${it.version}" }
                }
            }
        }

        check(resolvedLog4j.isNotEmpty()) {
            "No Log4j components were found in production runtime classpaths."
        }

        val unexpectedVersions = resolvedLog4j.filterNot {
            it.substringAfterLast('\t') == approvedLog4jVersion
        }
        check(unexpectedVersions.isEmpty()) {
            "Unexpected Log4j runtime versions:\n${unexpectedVersions.distinct().sorted().joinToString("\n")}"
        }

        val bootComponents =
            resolvedLog4j
                .filter { it.startsWith(":j-store-boot\t") }
                .map { it.split('\t')[1] }
                .toSet()
        val requiredBootComponents = setOf("log4j-api", "log4j-to-slf4j")
        check(bootComponents.containsAll(requiredBootComponents)) {
            "j-store-boot is missing required Log4j runtime components: " +
                (requiredBootComponents - bootComponents).sorted().joinToString()
        }

        val verifiedModules = resolvedLog4j.map { it.substringBefore('\t') }.toSet().size
        val verifiedComponents = resolvedLog4j.map { it.split('\t')[1] }.toSet().sorted()
        logger.lifecycle(
            "Verified Log4j {} across {} runtime classpaths: {}.",
            approvedLog4jVersion,
            verifiedModules,
            verifiedComponents.joinToString(),
        )
    }
}

tasks.register("verifyLicenseArtifacts") {
    group = "verification"
    description = "Verifies every Gradle JAR contains the canonical Apache-2.0 license."
    dependsOn(allprojects.map { it.tasks.withType<Jar>() })
    inputs.files(projectLicenseFile, thirdPartyNoticeFile)

    doLast {
        val expectedLicense = projectLicenseFile.asFile.readBytes()
        val expectedThirdPartyNotice = thirdPartyNoticeFile.asFile.readBytes()
        val archiveFiles =
            allprojects
                .flatMap { project ->
                    project.tasks.withType<Jar>().map { it.archiveFile.get().asFile }
                }
                .filter { it.isFile }
                .distinct()

        check(archiveFiles.isNotEmpty()) {
            "No JAR artifacts were produced for license verification."
        }

        val violations = mutableListOf<String>()
        archiveFiles.forEach { archive ->
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry("META-INF/LICENSE")
                if (entry == null) {
                    violations += "${archive.relativeTo(rootDir)}: missing META-INF/LICENSE"
                } else if (!zip.getInputStream(entry).readBytes().contentEquals(expectedLicense)) {
                    violations +=
                        "${archive.relativeTo(rootDir)}: META-INF/LICENSE differs from root LICENSE"
                }
                val thirdPartyEntry = zip.getEntry("META-INF/THIRD_PARTY.md")
                if (thirdPartyEntry == null) {
                    violations += "${archive.relativeTo(rootDir)}: missing META-INF/THIRD_PARTY.md"
                } else if (
                    !zip.getInputStream(thirdPartyEntry)
                        .readBytes()
                        .contentEquals(expectedThirdPartyNotice)
                ) {
                    violations +=
                        "${archive.relativeTo(rootDir)}: META-INF/THIRD_PARTY.md differs from root THIRD_PARTY.md"
                }
            }
        }

        check(violations.isEmpty()) { violations.joinToString(separator = "\n") }
        logger.lifecycle("Verified Apache-2.0 license in ${archiveFiles.size} JAR artifacts.")
    }
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
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
        googleJavaFormat(libs.versions.google.java.format.get()).aosp()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlin {
        target(prePushTargets?.filter { it.extension == "kt" } ?: kotlinSourceTrees)
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("kotlinExamples") {
        target("docs/**/*.kt")
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"), "(?=//|package )")
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target(prePushTargets?.filter { it.name.endsWith(".gradle.kts") } ?: kotlinGradleFiles)
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val installSpotlessGitHooks by
    tasks.registering(Copy::class) {
        group = "Spotless"
        description = "Installs the repository's Spotless pre-commit and pre-push hooks."
        from(layout.projectDirectory.dir("scripts/git-hooks")) {
            include("pre-commit", "pre-push")
        }
        into(layout.projectDirectory.dir(".git/hooks"))
        outputs.upToDateWhen { false }
        doLast {
            listOf("pre-commit", "pre-push").forEach { hook ->
                layout.projectDirectory.file(".git/hooks/$hook").asFile.setExecutable(true)
            }
        }
    }

tasks.named("spotlessInstallGitPrePushHook") {
    finalizedBy(installSpotlessGitHooks)
}

allprojects {
    tasks.cyclonedxDirectBom {
        includeConfigs = listOf("runtimeClasspath")
    }
}
