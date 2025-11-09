package fr.simplex_software.workshop.customer_service_ecs.cdk.config;

import io.smallrye.config.*;

@ConfigMapping(prefix = "cdk.infrastructure")
public interface InfrastructureConfig
{
  VpcConfig vpc();
  EcsConfig ecs();
  DatabaseConfig database();
  RedisConfig redis();
  LoggingConfig logging();

  interface VpcConfig {
    @WithDefault("2")
    int maxAzs();

    @WithDefault("1")
    int natGateways();
  }

  interface EcsConfig {
    @WithDefault("256")
    int cpu();

    @WithDefault("512")
    int memoryLimitMiB();

    @WithDefault("2")
    int desiredCount();

    @WithDefault("60")
    int healthCheckGracePeriodSeconds();

    @WithDefault("customer-service")
    String serviceName();
  }

  interface DatabaseConfig {
    @WithDefault("BURSTABLE3")
    String instanceClass();

    @WithDefault("MICRO")
    String instanceSize();

    @WithDefault("customers")
    String databaseName();

    @WithDefault("postgres")
    String secretUsername();

    @WithDefault("false")
    boolean deletionProtection();
  }

  interface RedisConfig {
    @WithDefault("cache.t3.micro")
    String nodeType();

    @WithDefault("1")
    int numNodes();

    @WithDefault("customer-cache")
    String clusterId();

    @WithDefault("Redis cache for customer service")
    String description();
  }

  interface LoggingConfig {
    @WithDefault("/ecs/customer-service")
    String logGroupName();

    @WithDefault("ONE_WEEK")
    String retentionDays();

    @WithDefault("ecs")
    String streamPrefix();
  }
}
