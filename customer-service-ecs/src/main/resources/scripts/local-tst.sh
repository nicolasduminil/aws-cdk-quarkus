#!/bin/bash
echo ">>> Starting local development environment..."
docker-compose up -d
echo ">>> Waiting for PostgreSQL..."
until docker-compose exec database pg_isready -U postgres; do sleep 1; done
echo ">>> Waiting for Redis..."
until docker-compose exec redis redis-cli ping; do sleep 1; done
echo ">>> Services ready! Starting Quarkus in dev mode..."
mvn clean test
docker compose down