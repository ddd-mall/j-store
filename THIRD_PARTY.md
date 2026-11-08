# Third-Party Software

The j-store application source is original work owned by 潘少峰 (Peter Pan) and licensed under Apache-2.0. This file records software that is checked into the repository but is owned by other copyright holders. It does not replace the license notices shipped by those components.

## Vendored Or Generated Files

| Files | Copyright holder | Source | License | Purpose |
|---|---|---|---|---|
| `gradlew`, `gradlew.bat`, `gradle/wrapper/**` | The original Gradle authors | <https://github.com/gradle/gradle> | Apache-2.0 | Reproducible Gradle bootstrap |

The Gradle Wrapper files retain their upstream notices. The j-store copyright header must not be added to them.

## Resolved Dependencies

JVM dependencies are not vendored into the source tree. Every module is audited by Licensee, which writes machine-readable reports to `build/reports/licensee/`. The release evidence bundle includes these reports together with the CycloneDX SBOM.

Unknown, missing, or unapproved dependency licenses fail the build. An exception must identify an exact artifact version and document the verified upstream license; group-wide and transitive ignores are not accepted.

### Frontend dependencies

Frontend packages are resolved by the root `pnpm-lock.yaml` and are not vendored. Run `pnpm licenses list --json` for the installed inventory and `pnpm licenses list --prod --json` for runtime dependencies. The Gradle Licensee and JVM SBOM checks do not cover npm packages. See `apps/admin-web/README.md` for frontend verification commands. License inventory does not replace upstream notices required when distributing bundled assets.

### Spring Authorization Server JDBC schemas

The OAuth protocol table declarations in the current Flyway baseline and database initialization snapshot are adapted from Spring Authorization Server 1.5.8 schema resources, with PostgreSQL text/timestamptz types and local indexes. Source: https://github.com/spring-projects/spring-authorization-server/tree/1.5.8/oauth2-authorization-server/src/main/resources/org/springframework/security/oauth2/server/authorization . Licensed under Apache License 2.0; copyright the Spring Authorization Server authors. Other tables in these files remain original j-store code.
