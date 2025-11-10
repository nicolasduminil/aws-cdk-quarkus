package fr.simplex_software.workshop.customer_service_ecs.cdk;

import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.*;
import fr.simplex_software.workshop.customer_service_ecs.cdk.config.InfrastructureConfig;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.*;
import software.amazon.awscdk.services.rds.*;

import java.util.*;

@Singleton
public class CustomerManagementStack extends Stack {

  @ConfigProperty(name = "cdk.image_name")
  String imageName;

  @ConfigProperty(name = "cdk.container.port", defaultValue = "8080")
  int containerPort;

  @Inject
  InfrastructureConfig config;

  @Inject
  public CustomerManagementStack(final App scope,
    final @ConfigProperty(name = "cdk.stack-id", defaultValue = "QuarkusCustomerManagementStack") String stackId,
    final StackProps props) {
    super(scope, stackId, props);
  }

  public void initStack() {
    Vpc vpc = Vpc.Builder.create(this, "CustomerVpc")
      .maxAzs(config.vpc().maxAzs())
      .natGateways(config.vpc().natGateways())
      .build();

    DatabaseInstance database = DatabaseInstance.Builder.create(this, "CustomerDatabase")
      .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
        .version(PostgresEngineVersion.VER_17_6)
        .build()))
      .instanceType(InstanceType.of(
        InstanceClass.valueOf(config.database().instanceClass()),
        InstanceSize.valueOf(config.database().instanceSize())))
      .vpc(vpc)
      .credentials(Credentials.fromGeneratedSecret(config.database().secretUsername()))
      .databaseName(config.database().databaseName())
      .deletionProtection(config.database().deletionProtection())
      .removalPolicy(RemovalPolicy.DESTROY)
      .build();

    CfnSubnetGroup redisSubnetGroup = CfnSubnetGroup.Builder.create(this, "RedisSubnetGroup")
      .description("Subnet group for Redis")
      .subnetIds(vpc.getPrivateSubnets().stream()
        .map(ISubnet::getSubnetId)
        .toList())
      .build();

    RedisCluster redis = new RedisCluster(this, "CustomerCache",
      RedisClusterProps.builder()
        .vpc(vpc)
        .subnetGroup(redisSubnetGroup)
        .clusterId(config.redis().clusterId())
        .description(config.redis().description())
        .nodeType(config.redis().nodeType())
        .numNodes(config.redis().numNodes())
        .build());

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
        .cpu(config.ecs().cpu())
        .memoryLimitMiB(config.ecs().memoryLimitMiB())
        .desiredCount(config.ecs().desiredCount())
        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
          .image(ContainerImage.fromRegistry(imageName))
          .containerPort(containerPort)
          .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(logGroup)
            .streamPrefix(config.logging().streamPrefix())
            .build()))
          .environment(Map.of(
            "QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://" + database.getInstanceEndpoint().getHostname() + ":5432/" + config.database().databaseName(),
            "QUARKUS_REDIS_HOSTS", "redis://" + redis.getPrimaryEndpoint() + ":6379"
          ))
          .secrets(Map.of(
            "QUARKUS_DATASOURCE_USERNAME", Secret.fromSecretsManager(database.getSecret(), "username"),
            "QUARKUS_DATASOURCE_PASSWORD", Secret.fromSecretsManager(database.getSecret(), "password")
          ))
          .build())
        .publicLoadBalancer(true)
        .healthCheckGracePeriod(Duration.seconds(config.ecs().healthCheckGracePeriodSeconds()))
        .serviceName(config.ecs().serviceName())
        .build();

    fargateService.getTargetGroup()
      .configureHealthCheck(HealthCheck.builder()
        .path("/q/health")
        .build());

    fargateService.getTaskDefinition().getExecutionRole().addManagedPolicy(
      ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy"));

    database.getConnections().allowFrom(fargateService.getService(), Port.tcp(5432));
    redis.allowConnectionsFrom(fargateService.getService());

    CfnOutput.Builder.create(this, "CustomerServiceLoadBalancerDNS")
      .value(fargateService.getLoadBalancer().getLoadBalancerDnsName())
      .exportName("CustomerServiceLoadBalancerDNS")
      .build();

    CfnOutput.Builder.create(this, "DatabaseEndpoint")
      .value(database.getInstanceEndpoint().getHostname())
      .build();
  }
}
