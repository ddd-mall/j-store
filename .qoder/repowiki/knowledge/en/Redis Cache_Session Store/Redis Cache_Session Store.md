---
kind: external_dependency
name: Redis Cache/Session Store
slug: redis
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

Used for caching, session management (JWT token storage), and distributed locking. Configured via Spring Data Redis with connection properties from JSTORE_REDIS_* environment variables. Local development runs via Docker Compose.