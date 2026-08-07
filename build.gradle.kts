import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import java.util.zip.ZipFile
import org.gradle.api.tasks.bundling.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.lombok) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.plugin.spring)
    id("app.cash.licensee") version "1.14.1" apply false
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
    apply(plugin = "app.cash.licensee")
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
        unusedAction(UnusedAction.IGNORE)
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

spotless {
    ratchetFrom("origin/master")

    java {
        target("**/src/**/*.java")
        targetExclude("**/build/**", "**/bin/**")
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
        googleJavaFormat("1.35.0").aosp()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/bin/**")
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
        ktfmt("0.63").kotlinlangStyle()
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
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**", "**/bin/**")
        ktfmt("0.63").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    tasks.cyclonedxDirectBom {
        includeConfigs = listOf("runtimeClasspath")
    }
}
