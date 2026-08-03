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

Primary relational database used by j-store application. Configured via JDBC connection in application-local.properties with schema 'develop'. Docker Compose setup provides local development environment. All domain data including outbox_entry and domain_event_consumption tables are stored here.