package fr.simplex_software.workshop.cdk.common.constructs;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.*;
import software.constructs.*;

import java.util.*;

public class RedisCluster extends Construct
{
  private final CfnReplicationGroup replicationGroup;
  private final SecurityGroup securityGroup;

  public RedisCluster(Construct scope, String id, RedisClusterProps props)
  {
    super(scope, id);

    this.securityGroup = SecurityGroup.Builder.create(this, "SecurityGroup")
      .vpc(props.vpc())
      .description("Security group for Redis cluster " + props.clusterId())
      .allowAllOutbound(false)
      .build();

    this.replicationGroup = CfnReplicationGroup.Builder.create(this, "ReplicationGroup")
      .replicationGroupId(props.clusterId())
      .replicationGroupDescription(props.description())
      .cacheNodeType(props.nodeType())
      .engine("redis")
      .numCacheClusters(props.numNodes())
      .cacheSubnetGroupName(props.subnetGroup().getRef())
      .securityGroupIds(List.of(securityGroup.getSecurityGroupId()))
      .automaticFailoverEnabled(false)
      .build();
  }

  public String getPrimaryEndpoint()
  {
    return replicationGroup.getAttrPrimaryEndPointAddress();
  }

  public void allowConnectionsFrom(IConnectable source)
  {
    securityGroup.addIngressRule(
      Peer.securityGroupId(source.getConnections().getSecurityGroups().getFirst().getSecurityGroupId()),
      Port.tcp(6379),
      "Redis access from " + source.getConnections().getSecurityGroups().getFirst().getSecurityGroupId()
    );
  }
}