---
kind: dependency_management
name: Gradle Version Catalogs with Centralized Dependency Management
category: dependency_management
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - build.gradle.kts
    - settings.gradle.kts
    - .github/dependabot.yml
    - gradle.properties
---

The J-Store platform uses Gradle as its build system with a centralized dependency management strategy built around Gradle's version catalogs (libs.versions.toml). This approach provides unified version control across all modules while maintaining clean, readable dependency declarations.

**Centralized Version Management**: All third-party library versions are declared in `gradle/libs.versions.toml`, which defines both version numbers and library aliases. This single source of truth ensures consistency across the multi-module project. The catalog includes Spring Boot 3.5.16, Kotlin 2.3.0, Spring Cloud 2023.0.5, and various other dependencies like Guava, Jackson, Netty, and testing frameworks.

**Module-Level Dependencies**: Each module's `build.gradle.kts` references dependencies through the centralized catalog using `libs.<alias>` syntax. For example, `implementation(libs.spring.boot.starter.web)` instead of hard-coded version strings. This pattern is consistently applied across all modules including domain, application, infrastructure, and boot layers.

**Repository Configuration**: The root `build.gradle.kts` configures Maven repositories in priority order: Maven Central, Aliyun mirror (`https://maven.aliyun.com/repository/public`), and local Maven repository. Individual modules can override this configuration when needed.

**Platform Dependencies**: The project leverages Spring Boot's dependency management platform (`spring-boot-dependencies`) to manage transitive dependency versions automatically. Additional BOMs are used for Jackson (`jackson-bom`) and Netty (`netty-bom`) to ensure version consistency within those ecosystems.

**Automated Updates**: GitHub Dependabot is configured to automatically create pull requests for dependency updates on a weekly schedule (Monday at 02:00 Asia/Shanghai timezone). It groups related test libraries together and applies consistent labeling and commit message prefixes.

**Build Toolchain**: Java 25 is enforced through Gradle toolchains, ensuring consistent compilation environments across the project. The project also uses Spotless for code formatting enforcement during builds.

**Private Registry Support**: The configuration supports private registries through the Aliyun mirror and local Maven repository, providing flexibility for internal dependencies while maintaining access to public artifacts.