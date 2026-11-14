# Build System & Dependency Management

## Independent Workspaces

The backend Gradle build and all JVM modules live in `backend/`. The frontend pnpm workspace, Node configuration and lockfile live in `frontend/`. Repository-root scripts locate the correct build; shared LICENSE, THIRD_PARTY and Spotless/hook resources remain outside the backend build root.

**Sources** · [Backend commands:3-19](file:///Users/101bjb0467/Sources/j-store/backend/README.md#L3-L19) · [Shared build resources:37-50](file:///Users/101bjb0467/Sources/j-store/backend/build.gradle.kts#L37-L50)

## Declared Toolchain and Versions

| Declaration | Current value |
| --- | --- |
| Java toolchain | 25 |
| Kotlin | 2.4.10 |
| Spring Boot | 3.5.16 |
| JUnit BOM | 6.1.2 |
| Kotest | 6.2.3 |
| Spotless / ktfmt | 8.9.0 / 0.63 |

These are repository declarations, not independently verified resolved versions. The catalog uses JUnit Jupiter aliases and `kotlin-test-junit5`; those alias names do not make the declared JUnit BOM version 5.

**Sources** · [Catalog:1-57](file:///Users/101bjb0467/Sources/j-store/backend/gradle/libs.versions.toml#L1-L57) · [Java toolchain:20-23](file:///Users/101bjb0467/Sources/j-store/backend/build.gradle.kts#L20-L23)

## Dependency Ownership

`backend/gradle/libs.versions.toml` is the coordinate and tool-version entry point. `j-store-dependencies-platform` owns BOM imports, family alignment and approved constraints. Consumer module scripts express scopes and project dependencies, not an alternative version strategy. Common build configuration injects the Platform into implementation, tests and test fixtures.

Do not introduce scattered GAV versions, dynamic versions, module-level force rules or automatic dependency-upgrade PRs. Dependency changes require explicit initiation, focused compatibility/security/license evidence and actual `dependencyInsight` results. Catalog declarations alone do not establish runtime resolution or absence of vulnerabilities.

**Sources** · [Platform injection:54-71](file:///Users/101bjb0467/Sources/j-store/backend/build.gradle.kts#L54-L71) · [Dependency rules:10-59](file:///Users/101bjb0467/Sources/j-store/docs/steering/dependency-management-guidelines.md#L10-L59)

## Commands from Repository Root

```bash
./scripts/backend-gradle.sh projects
./scripts/backend-gradle.sh :j-store-order-domain:test :j-store-order-application:test
./scripts/backend-gradle.sh test
./scripts/backend-gradle.sh spotlessCheck
./scripts/backend-gradle.sh :j-store-boot:bootJar
./scripts/backend-gradle.sh :j-store-boot:cyclonedxDirectBom --no-daemon
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend dev
pnpm --dir frontend check
./scripts/quality-gate.sh
```

Inside `backend/`, `./gradlew` is also valid. Formatting must use the repository Spotless/ktfmt entry points. Run focused tests first, then broaden according to impact; record skipped or failed checks explicitly.

**Sources** · [Backend README:3-19](file:///Users/101bjb0467/Sources/j-store/backend/README.md#L3-L19) · [TDD and verification:45-63](file:///Users/101bjb0467/Sources/j-store/docs/steering/tdd-guidelines.md#L45-L63)

## Frontend State

The administration application declares React 19.3.1, Ant Design 6.7.0, TypeScript 5.9.3 and Vite 8.3.6. Its scripts cover type checking, ESLint, Vitest, Playwright, Prettier and OpenAPI type generation. The current UI remains a blank entry with 404 fallback; installed tooling does not imply generated API contracts, business screens or authentication integration.

**Sources** · [Frontend scripts and dependencies:7-48](file:///Users/101bjb0467/Sources/j-store/frontend/apps/admin-web/package.json#L7-L48) · [Frontend scope:59-64](file:///Users/101bjb0467/Sources/j-store/docs/project-overview.md#L59-L64)
