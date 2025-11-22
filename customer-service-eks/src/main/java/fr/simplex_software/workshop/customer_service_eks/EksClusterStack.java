package fr.simplex_software.workshop.customer_service_eks;

import fr.simplex_software.workshop.cdk.common.config.*;
import fr.simplex_software.workshop.cdk.common.stacks.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import org.yaml.snakeyaml.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.cdk.lambdalayer.kubectl.v32.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.eks.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

@ApplicationScoped
public class EksClusterStack extends Stack
{
  private Cluster cluster;
  private Vpc vpc;
  private fr.simplex_software.workshop.cdk.common.stacks.DatabaseStack databaseStack;
  private final InfrastructureConfig config;

  @Inject
  public EksClusterStack(App app, InfrastructureConfig config)
  {
    super(app, "EksClusterStack");
    this.config = config;
  }

  public void initStack()
  {
    vpc = Vpc.Builder.create(this, "EksVpc")
      .maxAzs(config.vpc().maxAzs())
      .natGateways(config.vpc().natGateways())
      .build();

    databaseStack = new DatabaseStack(this, "DatabaseStack", vpc, config);

    cluster = Cluster.Builder.create(this, "CustomerServiceCluster")
      .clusterName("customer-service-cluster")
      .version(KubernetesVersion.V1_34)
      .vpc(vpc)
      .kubectlLayer(new KubectlV32Layer(this, "KubectlLayer"))
      .defaultCapacity(0)
      .build();

    cluster.addFargateProfile("CustomerServiceProfile",
      FargateProfileOptions.builder()
        .fargateProfileName("customer-service-fargate-profile")
        .selectors(List.of(Selector.builder()
          .namespace("customer-service")
          .build()))
        .build());

    try
    {
      String yamlContent = Files.readString(Paths.get("src/main/resources/k8s/customer-service.yaml"));
      AtomicInteger counter = new AtomicInteger(0);
      new Yaml().loadAll(yamlContent).forEach(doc ->
        cluster.addManifest("CustomerServiceResource%d".formatted(counter.getAndIncrement()),
          (Map<String, Object>) doc)
      );
    }
    catch (IOException e)
    {
      throw new RuntimeException("Failed to read YAML file", e);
    }
  }

  public Cluster getCluster()
  {
    return cluster;
  }

  public Vpc getVpc()
  {
    return vpc;
  }

  public fr.simplex_software.workshop.cdk.common.stacks.DatabaseStack getDatabaseStack()
  {
    return databaseStack;
  }
}
