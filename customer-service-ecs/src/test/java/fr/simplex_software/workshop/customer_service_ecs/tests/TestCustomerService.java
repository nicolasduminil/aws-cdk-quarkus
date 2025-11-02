package fr.simplex_software.workshop.customer_service_ecs.tests;

import io.quarkus.test.junit.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.*;
import org.testcontainers.containers.localstack.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.*;

@QuarkusTest
@Testcontainers
public class TestCustomerService
{
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
    .withDatabaseName("customers")
    .withUsername("postgres")
    .withPassword("password");

  @Container
  static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
    .withExposedPorts(6379);

  @Test
  void testWithContainers() {
    System.out.println("PostgreSQL URL: " + postgres.getJdbcUrl());
    System.out.println("Redis port: " + redis.getMappedPort(6379));
  }
}
