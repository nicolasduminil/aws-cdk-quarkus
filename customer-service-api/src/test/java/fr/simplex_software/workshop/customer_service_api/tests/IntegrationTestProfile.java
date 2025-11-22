package fr.simplex_software.workshop.customer_service_api.tests;

import io.quarkus.test.junit.*;

import java.util.*;

public class IntegrationTestProfile implements QuarkusTestProfile {
  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
      "quarkus.datasource.db-kind", "postgresql",
      "quarkus.datasource.username", "nicolas",
      "quarkus.datasource.password", "dev123",
      "quarkus.datasource.jdbc.url", "jdbc:postgresql://localhost:5432/customers",
      "quarkus.datasource.devservices.enabled", "false",
      "quarkus.redis.hosts", "redis://localhost:6379",
      "quarkus.redis.devservices.enabled", "false"
    );
  }
}
