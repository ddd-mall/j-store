---
kind: external_dependency
name: PostgreSQL Database
slug: postgresql
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

Primary relational database for all domain data persistence. Used via Spring Data JPA with Hikari connection pooling. Local development runs through Docker Compose with custom schema 'develop'. Flyway handles database migrations. Connection details configured via JSTORE_DB_* environment variables.