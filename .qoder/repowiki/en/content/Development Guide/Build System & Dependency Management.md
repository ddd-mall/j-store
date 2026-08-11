# Build System & Dependency Management

<cite>
**Referenced Files in This Document**
- [settings.gradle.kts](file://settings.gradle.kts)
- [build.gradle.kts](file://build.gradle.kts)
- [gradle.properties](file://gradle.properties)
- [gradle/libs.versions.toml](file://gradle/libs.versions.toml)
- [gradle/gradle-daemon-jvm.properties](file://gradle/gradle-daemon-jvm.properties)
- [j-store-boot/build.gradle.kts](file://j-store-boot/build.gradle.kts)
- [j-store-common-core/build.gradle.kts](file://j-store-common-core/build.gradle.kts)
- [j-store-order-domain/build.gradle.kts](file://j-store-order-domain/build.gradle.kts)
- [j-store-order-application/build.gradle.kts](file://j-store-order-application/build.gradle.kts)
- [j-store-boot/Dockerfile](file://j-store-boot/Dockerfile)
- [docker-compose.postgres.yml](file://docker-compose.postgres.yml)
- [j-store-boot/src/main/resources/application.properties](file://j-store-boot/src/main/resources/application.properties)
- [j-store-boot/src/main/resources/application-local.properties](file://j-store-boot/src/main/resources/application-local.properties)
- [gradle/wrapper/gradle-wrapper.properties](file://gradle/wrapper/gradle-wrapper.properties)
- [scripts/check-agent-governance.sh](file://scripts/check-agent-governance.sh)
- [scripts/quality-gate.sh](file://scripts/quality-gate.sh)
- [tests/tooling/test_spotless_pre_push.py](file://tests/tooling/test_spotless_pre_push.py)
</cite>

## Update Summary
**Changes Made**
- Added new section on Enhanced Incremental Spotless Integration with Git Pre-push Hooks
- Updated Gradle Daemon JVM Configuration for Cross-platform Compatibility section
- Enhanced build configuration documentation for selective code formatting
- Added comprehensive coverage of the new pre-push hook functionality and testing

## Table of Contents
1. [Introduction](#introduction)
2. [Project Structure](#project-structure)
3. [Core Components](#core-components)
4. [Architecture Overview](#architecture-overview)
5. [Detailed Component Analysis](#detailed-component-analysis)
6. [Enhanced Incremental Spotless Integration](#enhanced-incremental-spotless-integration)
7. [Dependency Analysis](#dependency-analysis)
8. [Performance Considerations](#performance-considerations)
9. [Troubleshooting Guide](#troubleshooting-guide)
10. [Conclusion](#conclusion)
11. [Appendices](#appendices)

## Introduction
This document explains the Gradle-based build system and dependency management for J-Store. It covers the multi-module project structure, centralized version management via libs.versions.toml, environment-specific build configurations, adding new modules, configuring tasks, optimizing performance, managing external dependencies and security updates, Docker containerization, and troubleshooting strategies. The system now includes enhanced incremental Spotless integration with Git pre-push hooks and improved cross-platform Gradle daemon JVM configuration.

## Project Structure
J-Store is a multi-module Gradle project organized by domain layers (domain, application, infrastructure, boot) and shared libraries. The root settings file declares all included modules, while the root build script centralizes common configuration such as Java toolchain, repositories, code formatting, and BOM generation. Each module has its own build.gradle.kts declaring plugins, dependencies, and task customizations.

```mermaid
graph TB
subgraph "Root"
S["settings.gradle.kts"]
B["build.gradle.kts"]
GProps["gradle.properties"]
V["gradle/libs.versions.toml"]
D["gradle/gradle-daemon-jvm.properties"]
end
subgraph "Modules"
MBoot["j-store-boot"]
MCommonCore["j-store-common-core"]
MOrderDomain["j-store-order-domain"]
MOrderApp["j-store-order-application"]
end
S --> MBoot
S --> MCommonCore
S --> MOrderDomain
S --> MOrderApp
B --> MBoot
B --> MCommonCore
B --> MOrderDomain
B --> MOrderApp
V --> MBoot
V --> MCommonCore
V --> MOrderDomain
V --> MOrderApp
GProps --> B
D --> B
```

**Diagram sources**
- [settings.gradle.kts:14-83](file://settings.gradle.kts#L14-L83)
- [build.gradle.kts:1-105](file://build.gradle.kts#L1-L105)
- [gradle.properties:1-6](file://gradle.properties#L1-L6)
- [gradle/libs.versions.toml:1-113](file://gradle/libs.versions.toml#L1-L113)
- [gradle/gradle-daemon-jvm.properties:1-13](file://gradle/gradle-daemon-jvm.properties#L1-L13)

**Section sources**
- [settings.gradle.kts:14-83](file://settings.gradle.kts#L14-L83)
- [build.gradle.kts:1-105](file://build.gradle.kts#L1-L105)

## Core Components
- Centralized Version Catalog: All library versions and aliases are defined in libs.versions.toml and referenced across modules using alias(libs.*). This ensures consistent versions and simplifies upgrades.
- Root Build Configuration: Defines Java toolchain (Java 25), repositories (Maven Central, Aliyun mirror, local), Spotless formatting, and CycloneDX BOM generation.
- Module Builds: Each module applies Kotlin JVM plugin and declares dependencies with either api or implementation to control transitive exposure.
- Environment Configurations: Spring Boot profiles manage runtime behavior; local profile configures database, Redis, JWT, messaging mode, and Flyway schema handling.
- **New**: Enhanced Spotless Integration: Incremental code formatting with Git pre-push hooks for selective file processing.
- **New**: Cross-platform JVM Configuration: Automatic JVM toolchain resolution for different operating systems and architectures.

Key responsibilities:
- settings.gradle.kts: Declares modules and plugin management.
- build.gradle.kts: Global conventions, quality tools, and Spotless integration.
- gradle/libs.versions.toml: Single source of truth for dependency versions and aliases.
- gradle.properties: JVM args, worker limits, Kotlin execution strategy.
- gradle/gradle-daemon-jvm.properties: Cross-platform JVM toolchain URLs.
- Application properties: Profile-driven configuration for development and testing.

**Section sources**
- [gradle/libs.versions.toml:1-113](file://gradle/libs.versions.toml#L1-L113)
- [build.gradle.kts:1-105](file://build.gradle.kts#L1-L105)
- [gradle.properties:1-6](file://gradle.properties#L1-L6)
- [gradle/gradle-daemon-jvm.properties:1-13](file://gradle/gradle-daemon-jvm.properties#L1-L13)
- [j-store-boot/src/main/resources/application.properties:1-11](file://j-store-boot/src/main/resources/application.properties#L1-L11)
- [j-store-boot/src/main/resources/application-local.properties:1-45](file://j-store-boot/src/main/resources/application-local.properties#L1-L45)

## Architecture Overview
The build architecture follows a layered modular design:
- Domain modules encapsulate business logic and expose APIs via api dependencies.
- Application modules orchestrate use cases and depend on domain and contracts.
- Infrastructure modules provide persistence and external integrations.
- Boot modules assemble services and expose endpoints.
- **New**: Enhanced quality pipeline with incremental Spotless checks integrated into Git workflow.

```mermaid
graph LR
A["j-store-common-core"] --> B["j-store-order-domain"]
B --> C["j-store-order-application"]
C --> D["j-store-order-infrastructure"]
C --> E["j-store-order-boot"]
F["j-store-goods-*"] --> E
G["j-store-user-*"] --> E
H["j-store-payment-*"] --> E
I["j-store-fulfillment-*"] --> E
J["j-store-accounting-*"] --> E
K["Spotless Hook"] --> E
```

[No sources needed since this diagram shows conceptual workflow, not actual code structure]

## Detailed Component Analysis

### Centralized Dependency Management with libs.versions.toml
- Versions section defines canonical versions for frameworks and libraries.
- Libraries section maps aliases to module coordinates and versions.
- Plugins section centralizes plugin IDs and versions.
- Modules reference dependencies via alias(libs.*) ensuring consistency and reducing duplication.

Best practices:
- Always update versions in [versions]; avoid hardcoding versions in module builds.
- Use platform/BOM dependencies where available to align transitive versions.
- Group related libraries under logical sections for readability.

**Section sources**
- [gradle/libs.versions.toml:1-113](file://gradle/libs.versions.toml#L1-L113)

### Root Build Conventions
- Java toolchain set to Java 25 across the project.
- Repositories include Maven Central, Aliyun mirror, and local repository.
- Spotless enforces consistent code style for Java, Kotlin, and Gradle scripts.
- CycloneDX BOM generation configured for runtime classpath to support SBOM workflows.

**Section sources**
- [build.gradle.kts:1-105](file://build.gradle.kts#L1-L105)

### Module-Level Build Patterns
- j-store-common-core exposes core utilities and JSON libraries via api to downstream modules.
- j-store-order-domain uses java-test-fixtures to share test data without leaking implementation details.
- j-store-order-application depends on domain and integration contracts, keeping boundaries clear.
- j-store-boot aggregates multiple domain and boot modules and wires runtime dependencies like JPA, Redis, Flyway, and validation.

**Section sources**
- [j-store-common-core/build.gradle.kts:1-38](file://j-store-common-core/build.gradle.kts#L1-L38)
- [j-store-order-domain/build.gradle.kts:1-28](file://j-store-order-domain/build.gradle.kts#L1-L28)
- [j-store-order-application/build.gradle.kts:1-31](file://j-store-order-application/build.gradle.kts#L1-L31)
- [j-store-boot/build.gradle.kts:1-96](file://j-store-boot/build.gradle.kts#L1-L96)

### Environment-Specific Build and Runtime Configuration
- Default profile activates local configuration.
- Local profile sets server port, datasource pool, Flyway schemas, Redis connection, JWT secret, logging level, outbox enablement, messaging mode, and merchant ID.
- Profiles allow separation of concerns between dev/test/prod environments.

**Section sources**
- [j-store-boot/src/main/resources/application.properties:1-11](file://j-store-boot/src/main/resources/application.properties#L1-L11)
- [j-store-boot/src/main/resources/application-local.properties:1-45](file://j-store-boot/src/main/resources/application-local.properties#L1-L45)

### Docker Build Process and Containerization
- Dockerfile uses Amazon Corretto 25 headless base image, copies built JAR, runs as non-root user, and sets entrypoint to execute the JAR.
- docker-compose defines PostgreSQL and Redis services with health checks, volumes, and environment variables for local development.

```mermaid
sequenceDiagram
participant Dev as "Developer"
participant Gradle as "Gradle Build"
participant Docker as "Docker Engine"
participant Image as "Container Image"
participant Run as "Running App"
Dev->>Gradle : ./gradlew : j-store-boot : build
Gradle-->>Dev : Build artifacts (JAR)
Dev->>Docker : docker build -f j-store-boot/Dockerfile .
Docker-->>Image : Create image from JAR
Dev->>Run : docker run app.jar
Run-->>Dev : Application running with env/config
```

**Diagram sources**
- [j-store-boot/Dockerfile:1-6](file://j-store-boot/Dockerfile#L1-L6)
- [docker-compose.postgres.yml:1-40](file://docker-compose.postgres.yml#L1-L40)

**Section sources**
- [j-store-boot/Dockerfile:1-6](file://j-store-boot/Dockerfile#L1-L6)
- [docker-compose.postgres.yml:1-40](file://docker-compose.postgres.yml#L1-L40)

### Adding New Modules
Steps:
1. Add include("module-name") in settings.gradle.kts.
2. Create module directory with src/main/kotlin and src/test/kotlin.
3. Write build.gradle.kts applying kotlin.jvm and necessary plugins.
4. Declare dependencies using alias(libs.*) and project(":other-module") references.
5. If exposing public APIs, use api() for consumers; otherwise use implementation().
6. Configure tests and toolchains consistently with other modules.

**Section sources**
- [settings.gradle.kts:14-83](file://settings.gradle.kts#L14-L83)
- [j-store-order-domain/build.gradle.kts:1-28](file://j-store-order-domain/build.gradle.kts#L1-L28)

### Configuring Build Tasks
- Test execution uses JUnit Platform across modules.
- Tar tasks exclude duplicates to produce clean archives.
- KAPT keeps Java annotation processors enabled for compatibility.
- Spotless integrates formatting checks into the build pipeline.

**Section sources**
- [j-store-boot/build.gradle.kts:85-96](file://j-store-boot/build.gradle.kts#L85-L96)
- [build.gradle.kts:31-105](file://build.gradle.kts#L31-L105)

### Optimizing Build Performance
- Increase JVM heap and metaspace via org.gradle.jvmargs in gradle.properties.
- Limit parallel workers with org.gradle.workers.max to balance CPU usage.
- Enable Kotlin in-process compilation for faster iteration.
- Use Gradle wrapper pinned to a specific distribution for reproducibility.
- Leverage incremental builds by minimizing unnecessary changes and avoiding heavy annotation processing.

**Section sources**
- [gradle.properties:1-6](file://gradle.properties#L1-L6)
- [gradle/wrapper/gradle-wrapper.properties:1-8](file://gradle/wrapper/gradle-wrapper.properties#L1-8)

### Managing External Dependencies and Security Updates
- Centralize versions in libs.versions.toml to simplify audits and upgrades.
- Prefer BOM/platform dependencies to align transitive versions.
- Integrate vulnerability scanning via CycloneDX BOM generation for SBOM analysis.
- Use Dependabot or similar tools to monitor updates and propose PRs.
- Regularly review and update critical libraries (e.g., Spring Boot, Netty, Jackson).

**Section sources**
- [gradle/libs.versions.toml:1-113](file://gradle/libs.versions.toml#L1-L113)
- [build.gradle.kts:100-105](file://build.gradle.kts#L100-L105)

## Enhanced Incremental Spotless Integration

### Overview
J-Store now features an enhanced incremental Spotless integration that provides intelligent code formatting through Git pre-push hooks. This system selectively formats only the files that have been modified or added in the current push, significantly improving developer experience and build performance.

### Key Features
- **Selective File Processing**: Only processes Kotlin (.kt), Java (.java), and Gradle Kotlin (.gradle.kts) files that are part of the current push
- **Automatic Installation**: One-time setup command installs the pre-push hook automatically
- **Smart Skipping**: Skips Spotless checks for documentation-only pushes (README.md, etc.)
- **Auto-formatting**: Automatically formats code issues and prompts developers to commit formatted changes
- **Cross-platform Support**: Works consistently across Windows, macOS, and Linux environments

### Installation and Setup
The pre-push hook can be installed with a single command:

```bash
./gradlew spotlessInstallGitPrePushHook
```

This command:
1. Copies the pre-push hook script from `scripts/git-hooks/pre-push` to `.git/hooks/pre-push`
2. Sets executable permissions on the hook script
3. Integrates with the existing Spotless workflow

### How It Works
The incremental Spotless system operates through several mechanisms:

1. **File Detection**: Analyzes git diff to identify changed Kotlin, Java, and Gradle files
2. **Selective Formatting**: Runs Spotless only on relevant files instead of the entire codebase
3. **Environment Variables**: Supports `SPOTLESS_PRE_PUSH_DRY_RUN` for testing without actual formatting
4. **Integration Testing**: Comprehensive test suite validates hook behavior across different scenarios

### Build Configuration
The root build script includes sophisticated Spotless configuration:

```kotlin
val prePushTargetFile = providers.gradleProperty("spotlessFilesFile").orNull
val prePushTargets = prePushTargetFile?.let { path ->
    val targetList = rootProject.file(path)
    require(targetList.isFile) { "Spotless target list does not exist: $targetList" }
    targetList.readLines().filter(String::isNotBlank).map(rootProject::file).filter(File::isFile)
}
```

This configuration enables:
- Dynamic target file specification via Gradle properties
- Fallback to full codebase scanning when no target file is provided
- Ratchet from origin/master for branch-based formatting

### Testing and Validation
The system includes comprehensive unit tests that verify:
- Documentation-only pushes skip Spotless checks
- Source code changes trigger appropriate formatting
- Build script modifications are properly detected and processed
- Cross-platform shell compatibility

**Section sources**
- [build.gradle.kts:31-105](file://build.gradle.kts#L31-L105)
- [tests/tooling/test_spotless_pre_push.py:1-85](file://tests/tooling/test_spotless_pre_push.py#L1-85)

### Best Practices
- Always install the pre-push hook for consistent code formatting
- Use `./gradlew spotlessCheck` for full repository validation before major releases
- Commit formatted code after automatic formatting during push attempts
- Leverage dry-run mode for testing hook behavior in CI/CD pipelines

## Gradle Daemon JVM Configuration

### Cross-platform Compatibility
J-Store now includes a sophisticated Gradle daemon JVM configuration system that automatically resolves appropriate JVM toolchains for different operating systems and architectures.

### Configuration Structure
The `gradle/gradle-daemon-jvm.properties` file contains platform-specific JVM download URLs:

```properties
toolchainUrl.FREE_BSD.AARCH64=https\://api.foojay.io/disco/v3.0/ids/97d46b5ff4df0bf61e95652a190a5567/redirect
toolchainUrl.LINUX.AARCH64=https\://api.foojay.io/disco/v3.0/ids/97d46b5ff4df0bf61e95652a190a5567/redirect
toolchainUrl.MAC_OS.AARCH64=https\://api.foojay.io/disco/v3.0/ids/1df82b73e2e128ef2e2e2e2e2e2e2e2e/redirect
toolchainUrl.WINDOWS.AARCH64=https\://api.foojay.io/disco/v3.0/ids/643f404f769811f5f267fd7af7af7af7/redirect
toolchainVersion=25
```

### Supported Platforms
- **FreeBSD**: AArch64 and X86_64 architectures
- **Linux**: AArch64 and X86_64 architectures
- **macOS**: AArch64 (Apple Silicon) and X86_64 architectures
- **Windows**: AArch64 and X86_64 architectures
- **Unix**: Generic Unix-like systems with AArch64 support

### Benefits
- **Consistent Builds**: Ensures all developers use the same JVM version
- **Automatic Downloads**: Downloads required JVM distributions automatically
- **Architecture Awareness**: Selects appropriate JVM for host architecture
- **Reduced Setup Time**: Eliminates manual JVM installation requirements

**Section sources**
- [gradle/gradle-daemon-jvm.properties:1-13](file://gradle/gradle-daemon-jvm.properties#L1-L13)
- [settings.gradle.kts:8-10](file://settings.gradle.kts#L8-L10)

## Dependency Analysis
The following diagram illustrates key module dependencies and their relationships:

```mermaid
graph TB
CommonCore["j-store-common-core"]
OrderDomain["j-store-order-domain"]
OrderApp["j-store-order-application"]
Boot["j-store-boot"]
CommonCore --> OrderDomain
OrderDomain --> OrderApp
OrderApp --> Boot
```

**Diagram sources**
- [j-store-common-core/build.gradle.kts:19-28](file://j-store-common-core/build.gradle.kts#L19-L28)
- [j-store-order-domain/build.gradle.kts:11-19](file://j-store-order-domain/build.gradle.kts#L11-L19)
- [j-store-order-application/build.gradle.kts:10-22](file://j-store-order-application/build.gradle.kts#L10-L22)
- [j-store-boot/build.gradle.kts:28-44](file://j-store-boot/build.gradle.kts#L28-L44)

**Section sources**
- [j-store-common-core/build.gradle.kts:19-28](file://j-store-common-core/build.gradle.kts#L19-L28)
- [j-store-order-domain/build.gradle.kts:11-19](file://j-store-order-domain/build.gradle.kts#L11-L19)
- [j-store-order-application/build.gradle.kts:10-22](file://j-store-order-application/build.gradle.kts#L10-L22)
- [j-store-boot/build.gradle.kts:28-44](file://j-store-boot/build.gradle.kts#L28-L44)

## Performance Considerations
- Use api vs implementation judiciously to reduce compile-time dependencies and improve incremental builds.
- Avoid pulling heavy libraries into core modules unless necessary.
- Keep test fixtures isolated to prevent unintended coupling.
- Pin Gradle wrapper version to ensure consistent build times across environments.
- Monitor build logs for slow tasks and consider splitting large modules if needed.
- **New**: Leverage incremental Spotless to reduce formatting overhead during development.
- **New**: Utilize cross-platform JVM configuration to eliminate manual setup time.

## Troubleshooting Guide
Common issues and resolutions:
- Java toolchain mismatch: Ensure Java 25 is installed and configured; verify toolchain settings in build files.
- Dependency resolution failures: Check repository mirrors and network access; validate versions in libs.versions.toml.
- Test discovery failures: Confirm JUnit Platform configuration and test annotations.
- Docker build errors: Verify JAR path and base image availability; ensure non-root user permissions.
- Profile misconfiguration: Validate active profiles and environment variable defaults.
- **New**: Spotless hook installation issues: Run `./gradlew spotlessInstallGitPrePushHook` with proper permissions.
- **New**: Cross-platform JVM issues: Verify gradle-daemon-jvm.properties contains correct URLs for your platform.

**Section sources**
- [build.gradle.kts:13-17](file://build.gradle.kts#L13-L17)
- [j-store-boot/build.gradle.kts:11-19](file://j-store-boot/build.gradle.kts#L11-L19)
- [j-store-boot/src/main/resources/application.properties:1-11](file://j-store-boot/src/main/resources/application.properties#L1-L11)
- [j-store-boot/Dockerfile:1-6](file://j-store-boot/Dockerfile#L1-L6)

## Conclusion
J-Store's Gradle build system leverages a centralized version catalog, consistent root conventions, and modular architecture to maintain clarity, scalability, and performance. The enhanced incremental Spotless integration with Git pre-push hooks provides intelligent code formatting that improves developer experience while maintaining code quality standards. The cross-platform Gradle daemon JVM configuration ensures consistent builds across different operating systems and architectures. By adhering to best practices for dependency management, environment configuration, and containerization, teams can streamline development, enhance security posture, and optimize build times even as the codebase grows.

## Appendices
- Gradle Wrapper: Pinned to a specific distribution for reproducible builds.
- Docker Compose: Provides PostgreSQL and Redis services with health checks for local development.
- SBOM Generation: CycloneDX configured to generate BOMs for runtime dependencies.
- **New**: Pre-push Hook: Automated code formatting integration with Git workflow.
- **New**: Cross-platform JVM: Automatic JVM toolchain resolution for different platforms.

**Section sources**
- [gradle/wrapper/gradle-wrapper.properties:1-8](file://gradle/wrapper/gradle-wrapper.properties#L1-8)
- [docker-compose.postgres.yml:1-40](file://docker-compose.postgres.yml#L1-40)
- [build.gradle.kts:100-105](file://build.gradle.kts#L100-L105)
- [scripts/quality-gate.sh:1-50](file://scripts/quality-gate.sh#L1-50)
- [gradle/gradle-daemon-jvm.properties:1-13](file://gradle/gradle-daemon-jvm.properties#L1-L13)
