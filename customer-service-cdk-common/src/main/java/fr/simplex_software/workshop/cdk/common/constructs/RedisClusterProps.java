package fr.simplex_software.workshop.cdk.common.constructs;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.*;

public record RedisClusterProps(
  Vpc vpc,
  CfnSubnetGroup subnetGroup,
  String clusterId,
  String description,
  String nodeType,
  int numNodes
) {}