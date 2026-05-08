j-store

Local service Docker deployment

This repository provides a Docker Compose file for local PostgreSQL and Redis.
It is aligned with j-store-boot/src/main/resources/application-local.properties.

Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)

Start local services

Run this command from the repository root:

docker-compose -f docker-compose.postgres.yml up -d

Check status:

docker-compose -f docker-compose.postgres.yml ps

Stop local services

docker-compose -f docker-compose.postgres.yml down

Remove database volume (dangerous: deletes all local DB data):

docker-compose -f docker-compose.postgres.yml down -v

Note:

- If your machine supports Docker Compose v2 plugin, the equivalent command is: docker compose -f docker-compose.postgres.yml up -d

PostgreSQL connection info

- Host: 192.168.31.213
- Port: 30432
- Database: j_store
- Username: develop
- Password: Jupeter104741
- Default schema: develop

Quick test with psql (if installed locally)

psql -h 192.168.31.213 -p 30432 -U develop -d j_store

Redis connection info

- Host: 192.168.31.213
- Port: 6379
- Password: empty
- Database: 0
