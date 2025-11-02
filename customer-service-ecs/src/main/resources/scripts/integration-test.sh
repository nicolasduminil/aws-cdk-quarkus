#!/bin/bash
echo "Running integration tests..."

# Start local services
docker-compose up -d

# Wait for services
until docker-compose exec postgres pg_isready -U postgres; do sleep 1; done
until docker-compose exec redis redis-cli ping; do sleep 1; done

# Run tests
./mvnw test -Dquarkus.profile=dev

# Cleanup
docker-compose down
