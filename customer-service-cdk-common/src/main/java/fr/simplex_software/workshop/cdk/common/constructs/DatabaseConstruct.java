package fr.simplex_software.workshop.cdk.common.constructs;

import fr.simplex_software.workshop.cdk.common.config.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.*;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.constructs.*;

import java.util.stream.*;

public class DatabaseConstruct extends Construct
{
  private final DatabaseInstance database;
  private final RedisCluster redis;

  public DatabaseConstruct(Construct scope, String id, Vpc vpc, InfrastructureConfig config)
  {
    super(scope, id);

    CfnSubnetGroup redisSubnetGroup = CfnSubnetGroup.Builder.create(this, "RedisSubnetGroup")
      .description("Subnet group for Redis")
      .subnetIds(vpc.getPrivateSubnets().stream()
        .map(ISubnet::getSubnetId)
        .collect(Collectors.toList()))
      .build();

    this.redis = new RedisCluster(this, "CustomerCache",
      new RedisClusterProps(
        vpc,
        redisSubnetGroup,
        config.redis().clusterId(),
        config.redis().description(),
        config.redis().nodeType(),
        config.redis().numNodes()
      ));

    this.database = DatabaseInstance.Builder.create(this, "CustomerDatabase")
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
      .storageEncrypted(true)
      .build();
  }

  public DatabaseInstance getDatabase()
  {
    return database;
  }

  public RedisCluster getRedis()
  {
    return redis;
  }
}