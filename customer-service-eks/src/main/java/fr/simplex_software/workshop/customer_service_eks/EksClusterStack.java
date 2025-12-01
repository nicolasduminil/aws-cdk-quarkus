package fr.simplex_software.workshop.customer_service_eks;

import fr.simplex_software.workshop.cdk.common.config.*;
import fr.simplex_software.workshop.cdk.common.stacks.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.*;
import org.yaml.snakeyaml.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.cdk.lambdalayer.kubectl.v32.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.eks.*;
import software.amazon.awscdk.services.iam.*;

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
  private static final String CDK_BUILT_IN_ACCOUNT = "arn:aws:iam::%s:role/cdk-*-cfn-exec-role-*";

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
      .mastersRole(Role.fromRoleArn(this, "AdminRole",
        CDK_BUILT_IN_ACCOUNT.formatted(Stack.of(this).getAccount() + "")))
      .build();

    cluster.addFargateProfile("CustomerServiceProfile",
      FargateProfileOptions.builder()
        .fargateProfileName("customer-service-fargate-profile")
        .selectors(List.of(Selector.builder()
          .namespace("customer-service")
          .build()))
        .build());

    cluster.addManifest("AppConfigMap", Map.of(
      "apiVersion", "v1",
      "kind", "ConfigMap",
      "metadata", Map.of(
        "name", "app-config",
        "namespace", "customer-service"
      ),
      "data", Map.of(
        "CDK_DEFAULT_ACCOUNT", Stack.of(this).getAccount(),
        "CDK_DEFAULT_REGION", Stack.of(this).getRegion(),
        "RDS_ENDPOINT", databaseStack.getDatabase().getDbInstanceEndpointAddress(),
        "REDIS_ENDPOINT", databaseStack.getRedis().getPrimaryEndpoint(),
        "DB_USERNAME", config.database().secretUsername()
      )
    ));

    try
    {
      String yamlContent = Files.readString(Paths.get("src/main/resources/k8s/customer-service.yaml"));
      yamlContent = yamlContent
        .replace("${CDK_DEFAULT_ACCOUNT}", Stack.of(this).getAccount())
        .replace("${CDK_DEFAULT_REGION}", Stack.of(this).getRegion())
        .replace("${RDS_ENDPOINT}", databaseStack.getDatabase().getInstanceEndpoint().getHostname())
        .replace("${REDIS_ENDPOINT}", databaseStack.getRedis().getPrimaryEndpoint())
        .replace("${DB_USERNAME}", config.database().secretUsername())
        //.replace("${DB_PASSWORD}", databaseStack.getDatabase().getSecret().secretValueFromJson("password").unsafeUnwrap());
        .replace("${DB_PASSWORD}", "CHANGEME");
      CfnOutput.Builder.create(this, "DatabaseSecretArn")
        .value(databaseStack.getDatabase().getSecret().getSecretArn())
        .exportName("DatabaseSecretArn")
        .build();
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
