---
kind: external_dependency
name: Redis Cache
slug: redis
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

In-memory data store configured for caching and session management. Connected via Spring Data Redis configuration in application-local.properties. Used as supporting infrastructure alongside PostgreSQL.