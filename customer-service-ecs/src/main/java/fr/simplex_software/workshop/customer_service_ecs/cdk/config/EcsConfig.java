package fr.simplex_software.workshop.customer_service_ecs.cdk.config;

import io.smallrye.config.*;

@ConfigMapping(prefix = "cdk.infrastructure.ecs")
public interface EcsConfig
{
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