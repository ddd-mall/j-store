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
