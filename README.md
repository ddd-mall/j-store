j-store

PostgreSQL Docker deployment

This repository provides a Docker Compose file for local PostgreSQL.
It is aligned with j-store-boot/src/main/resources/application-local.properties.

Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)

Start PostgreSQL

Run this command from the repository root:

docker-compose -f docker-compose.postgres.yml up -d

Check status:

docker-compose -f docker-compose.postgres.yml ps

Stop PostgreSQL

docker-compose -f docker-compose.postgres.yml down

Remove database volume (dangerous: deletes all local DB data):

docker-compose -f docker-compose.postgres.yml down -v

Note:

- If your machine supports Docker Compose v2 plugin, the equivalent command is: docker compose -f docker-compose.postgres.yml up -d

Connection info

- Host: localhost
- Port: 5432
- Database: develop
- Username: develop
- Password: Jupeter104741
- Default schema: develop

Quick test with psql (if installed locally)

psql -h localhost -p 5432 -U develop -d develop
