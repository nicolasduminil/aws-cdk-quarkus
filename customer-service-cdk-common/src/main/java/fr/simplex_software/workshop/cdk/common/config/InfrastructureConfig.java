package fr.simplex_software.workshop.cdk.common.config;

import io.smallrye.config.*;

@ConfigMapping(prefix = "cdk.infrastructure")
public interface InfrastructureConfig
{
  VpcConfig vpc();
  DatabaseConfig database();
  RedisConfig redis();
  LoggingConfig logging();

  interface VpcConfig
  {
    @WithDefault("2")
    int maxAzs();
    @WithDefault("1")
    int natGateways();
  }

  interface DatabaseConfig
  {
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

  interface RedisConfig
  {
    @WithDefault("cache.t3.micro")
    String nodeType();
    @WithDefault("1")
    int numNodes();
    @WithDefault("customer-cache")
    String clusterId();
    @WithDefault("Redis cache for customer service")
    String description();
  }

  interface LoggingConfig
  {
    @WithDefault("/aws/customer-service")
    String logGroupName();
    @WithDefault("ONE_WEEK")
    String retentionDays();
    @WithDefault("service")
    String streamPrefix();
  }
}