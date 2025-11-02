package fr.simplex_software.workshop.customer_service_ecs.infrastructure;

import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.rds.*;

public class RedisClusterProps
{
  private final Vpc vpc;
  private final SubnetGroup subnetGroup;
  private final String clusterId;
  private final String description;
  private final String nodeType;
  private final Integer numNodes;

  private RedisClusterProps(Builder builder) {
    this.vpc = builder.vpc;
    this.subnetGroup = builder.subnetGroup;
    this.clusterId = builder.clusterId;
    this.description = builder.description;
    this.nodeType = builder.nodeType;
    this.numNodes = builder.numNodes;
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public Vpc getVpc() { return vpc; }
  public SubnetGroup getSubnetGroup() { return subnetGroup; }
  public String getClusterId() { return clusterId; }
  public String getDescription() { return description; }
  public String getNodeType() { return nodeType; }
  public Integer getNumNodes() { return numNodes; }

  public static class Builder {
    private Vpc vpc;
    private SubnetGroup subnetGroup;
    private String clusterId;
    private String description = "Redis cluster";
    private String nodeType = "cache.t3.micro";
    private Integer numNodes = 1;

    public Builder vpc(Vpc vpc) { this.vpc = vpc; return this; }
    public Builder subnetGroup(SubnetGroup subnetGroup) { this.subnetGroup = subnetGroup; return this; }
    public Builder clusterId(String clusterId) { this.clusterId = clusterId; return this; }
    public Builder description(String description) { this.description = description; return this; }
    public Builder nodeType(String nodeType) { this.nodeType = nodeType; return this; }
    public Builder numNodes(Integer numNodes) { this.numNodes = numNodes; return this; }

    public RedisClusterProps build() {
      return new RedisClusterProps(this);
    }
  }
}
