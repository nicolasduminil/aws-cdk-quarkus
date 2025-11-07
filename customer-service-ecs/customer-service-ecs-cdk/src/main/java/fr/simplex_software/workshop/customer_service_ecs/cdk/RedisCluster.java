package fr.simplex_software.workshop.customer_service_ecs.cdk;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.*;
import software.constructs.*;

import java.util.*;

public class RedisCluster extends Construct
{
  private final CfnReplicationGroup replicationGroup;
  private final SecurityGroup securityGroup;
  private static final String DEFAULT_SG_DESCRIPTION = "Security group for Redis cluster %s";
  private static final String DEFAULT_ACCESS_DESCRIPTION = "Redis access from %s";

  public RedisCluster(Construct scope, String id, RedisClusterProps props)
  {
    super(scope, id);
    this.securityGroup = SecurityGroup.Builder.create(this, "SecurityGroup")
      .vpc(props.getVpc())
      .description(DEFAULT_SG_DESCRIPTION.formatted(props.getClusterId()))
      .allowAllOutbound(false)
      .build();
    this.replicationGroup = CfnReplicationGroup.Builder.create(this, "ReplicationGroup")
      .replicationGroupId(props.getClusterId())
      .replicationGroupDescription(props.getDescription())
      .cacheNodeType(props.getNodeType())
      .engine("redis")
      .numCacheClusters(props.getNumNodes())
      .cacheSubnetGroupName(props.getSubnetGroup().getRef())
      .securityGroupIds(List.of(securityGroup.getSecurityGroupId()))
      .automaticFailoverEnabled(false)
      .build();
  }

  public String getPrimaryEndpoint()
  {
    return replicationGroup.getAttrPrimaryEndPointAddress();
  }

  public SecurityGroup getSecurityGroup()
  {
    return securityGroup;
  }

  public void allowConnectionsFrom(IConnectable source)
  {
    securityGroup.addIngressRule(
      Peer.securityGroupId(source.getConnections().getSecurityGroups().getFirst().getSecurityGroupId()),
      Port.tcp(6379),
      DEFAULT_ACCESS_DESCRIPTION.formatted(source.getConnections().getSecurityGroups().getFirst().getSecurityGroupId())
    );
  }
}
