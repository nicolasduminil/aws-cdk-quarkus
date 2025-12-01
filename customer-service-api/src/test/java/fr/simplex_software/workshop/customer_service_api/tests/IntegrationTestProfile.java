package fr.simplex_software.workshop.customer_service_api.tests;

import io.quarkus.test.junit.*;

import java.util.*;

public class IntegrationTestProfile implements QuarkusTestProfile
{
  @Override
  public Map<String, String> getConfigOverrides()
  {
    return Map.of(
      "quarkus.rest-client.customers-api.url",
      System.getProperty("quarkus.rest-client.customers-api.url",
        "http://localhost:8080"),
      "quarkus.http.test-port", "0",
      "quarkus.datasource.devservices.enabled", "false",
      "quarkus.redis.devservices.enabled", "false",
      "quarkus.redis.hosts", "redis://localhost:6379",
      "quarkus.hibernate-orm.enabled", "false"
    );
  }
}
