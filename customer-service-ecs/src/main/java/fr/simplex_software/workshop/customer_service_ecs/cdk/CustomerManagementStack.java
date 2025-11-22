package fr.simplex_software.workshop.customer_service_ecs.cdk;

import fr.simplex_software.workshop.cdk.common.config.*;
import fr.simplex_software.workshop.cdk.common.constructs.*;
import fr.simplex_software.workshop.cdk.common.stacks.*;
import fr.simplex_software.workshop.customer_service_ecs.cdk.config.*;
import io.smallrye.config.*;
import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.*;

import java.util.*;

@Singleton
public class CustomerManagementStack extends Stack
{
  @Inject
  InfrastructureConfig config;
  @Inject
  EcsConfig ecsConfig;
  @ConfigProperty(name = "cdk.image_name")
  String imageName;
  @ConfigProperty(name = "cdk.container.port", defaultValue = "8080")
  int containerPort;

  @Inject
  public CustomerManagementStack(final App scope,
    final @ConfigProperty(name = "cdk.stack-id", defaultValue = "QuarkusCustomerManagementStack") String stackId,
    final StackProps props)
  {
    super(scope, stackId, props);
  }

  public void initStack()
  {
    Vpc vpc = Vpc.Builder.create(this, "CustomerVpc")
      .maxAzs(config.vpc().maxAzs())
      .natGateways(config.vpc().natGateways())
      .build();

    DatabaseConstruct dbConstruct = new DatabaseConstruct(this, "Database", vpc, config);
    Cluster cluster = Cluster.Builder.create(this, "CustomerCluster")
      .vpc(vpc)
      .build();

    LogGroup logGroup = LogGroup.Builder.create(this, "CustomerServiceLogGroup")
      .logGroupName(config.logging().logGroupName())
      .retention(RetentionDays.valueOf(config.logging().retentionDays()))
      .build();

    ApplicationLoadBalancedFargateService fargateService =
      ApplicationLoadBalancedFargateService.Builder.create(this, "CustomerService")
        .cluster(cluster)
        .cpu(ecsConfig.cpu())
        .memoryLimitMiB(ecsConfig.memoryLimitMiB())
        .desiredCount(ecsConfig.desiredCount())
        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
          .image(ContainerImage.fromRegistry(imageName))
          .containerPort(containerPort)
          .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(logGroup)
            .streamPrefix(config.logging().streamPrefix())
            .build()))
          .environment(Map.of(
            "QUARKUS_DATASOURCE_JDBC_URL",
            "jdbc:postgresql://" + dbConstruct.getDatabase().getInstanceEndpoint().getHostname() +
              ":5432/" + config.database().databaseName(),
            "QUARKUS_REDIS_HOSTS", "redis://" + dbConstruct.getRedis().getPrimaryEndpoint() + ":6379"
          ))
          .secrets(Map.of(
            "QUARKUS_DATASOURCE_USERNAME", software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbConstruct.getDatabase().getSecret(), "username"),
            "QUARKUS_DATASOURCE_PASSWORD", software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbConstruct.getDatabase().getSecret(), "password")
          ))
          .build())
        .publicLoadBalancer(true)
        .healthCheckGracePeriod(Duration.seconds(ecsConfig.healthCheckGracePeriodSeconds()))
        .serviceName(ecsConfig.serviceName())
        .minHealthyPercent(100)
        .build();

    fargateService.getTargetGroup()
      .configureHealthCheck(HealthCheck.builder()
        .healthyHttpCodes("200")
        .path("/q/health")
        .build());

    fargateService.getTaskDefinition().getExecutionRole().addManagedPolicy(
      ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy"));

    dbConstruct.getDatabase().getConnections().allowFrom(fargateService.getService(), Port.tcp(5432));
    dbConstruct.getRedis().allowConnectionsFrom(fargateService.getService());

    CfnOutput.Builder.create(this, "CustomerServiceLoadBalancerDNS")
      .value(fargateService.getLoadBalancer().getLoadBalancerDnsName())
      .exportName("CustomerServiceLoadBalancerDNS")
      .build();

    CfnOutput.Builder.create(this, "DatabaseEndpoint")
      .value(dbConstruct.getDatabase().getInstanceEndpoint().getHostname())
      .build();
  }
}