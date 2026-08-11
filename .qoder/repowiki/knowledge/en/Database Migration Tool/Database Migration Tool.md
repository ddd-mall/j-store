---
kind: external_dependency
name: Database Migration Tool
slug: flyway
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

Database version control tool used for managing PostgreSQL schema changes. Migrations stored in src/main/resources/db/migration/ directory. Integrated with Spring Boot for automatic schema updates during application startup.