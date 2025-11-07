package fr.simplex_software.workshop.customer_service_ecs.cdk;

import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;  // Add this import
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.logs.*;
import software.amazon.awscdk.services.rds.*;

import java.util.*;

@Singleton
public class CustomerManagementStack extends Stack
{
  @ConfigProperty(name = "cdk.image_name")
  String imageName;
  @Inject
  public CustomerManagementStack(final App scope,
    final @ConfigProperty(name = "cdk.stack-id",
      defaultValue = "QuarkusCustomerManagementStack") String stackId,
    final StackProps props)
  {
    super(scope, stackId, props);
  }

  public void initStack()
  {
    Vpc vpc = Vpc.Builder.create(this, "CustomerVpc")
      .maxAzs(2)
      .natGateways(1)
      .build();

    DatabaseInstance database = DatabaseInstance.Builder.create(this, "CustomerDatabase")
      .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
        .version(PostgresEngineVersion.VER_17_6)
        .build()))
      .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
      .vpc(vpc)
      .credentials(Credentials.fromGeneratedSecret("postgres"))
      .databaseName("customers")
      .deletionProtection(false)
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
        .clusterId("customer-cache")
        .description("Redis cache for customer service")
        .nodeType("cache.t3.micro")
        .numNodes(1).build());

    Cluster cluster = Cluster.Builder.create(this, "CustomerCluster")
      .vpc(vpc)
      .build();

    LogGroup logGroup = LogGroup.Builder.create(this, "CustomerServiceLogGroup")
      .logGroupName("/ecs/customer-service")
      .retention(RetentionDays.ONE_WEEK)
      .build();

    ApplicationLoadBalancedFargateService fargateService =
      ApplicationLoadBalancedFargateService.Builder.create(this, "CustomerService")
        .cluster(cluster)
        .cpu(256)
        .memoryLimitMiB(512)
        .desiredCount(2)
        .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
          .image(ContainerImage.fromRegistry(imageName))
          .containerPort(8080)
          .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(logGroup)
            .streamPrefix("ecs")
            .build()))
          .environment(Map.of(
            "DB_HOST", database.getInstanceEndpoint().getHostname(),
            "DB_PORT", "5432",
            "DB_NAME", "customers",
            "REDIS_HOST", redis.getPrimaryEndpoint(),
            "REDIS_PORT", "6379"
          ))
          .secrets(Map.of(
            "DB_USERNAME", Secret.fromSecretsManager(database.getSecret(), "username"),
            "DB_PASSWORD", Secret.fromSecretsManager(database.getSecret(), "password")
          ))
          .build())
        .publicLoadBalancer(true)
        .serviceName("customer-service")
        .build();
    fargateService.getTaskDefinition().getExecutionRole().addManagedPolicy(
      ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy"));
    database.getConnections().allowFrom(fargateService.getService(), Port.tcp(5432));
    redis.allowConnectionsFrom(fargateService.getService());
  }
}
