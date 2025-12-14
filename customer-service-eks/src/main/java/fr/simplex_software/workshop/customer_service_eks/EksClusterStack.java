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
import software.amazon.awscdk.services.iam.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

@ApplicationScoped
public class EksClusterStack extends Stack
{
  private FargateCluster cluster;
  private final Vpc vpc;
  private final DatabaseStack databaseStack;
  private final InfrastructureConfig config;

  @Inject
  public EksClusterStack(App app, InfrastructureConfig config, Vpc vpc, DatabaseStack databaseStack)
  {
    super(app, "EksClusterStack");
    this.config = config;
    this.vpc = vpc;
    this.databaseStack = databaseStack;
    this.addDependency(databaseStack);
  }

  public void initStack() throws IOException
  {
    createCluster();
    KubernetesManifest namespace = createNamespace();
    addFargateProfile();
    ServiceAccount serviceAccount = setupServiceAccountWithIAM();
    serviceAccount.getNode().addDependency(namespace);
    KubernetesManifest configMap = addConfigMap();
    configMap.getNode().addDependency(serviceAccount);
    addDeploymentAndService(configMap);
  }

  public FargateCluster getCluster()
  {
    return cluster;
  }

  public IVpc getVpc()
  {
    return vpc;
  }

  public DatabaseStack getDatabaseStack()
  {
    return databaseStack;
  }

  private void createCluster()
  {
    cluster = FargateCluster.Builder.create(this, "CustomerServiceCluster")
      .clusterName(config.eks().clusterName())
      .version(KubernetesVersion.V1_34)
      .vpc(vpc)
      .kubectlLayer(new KubectlV32Layer(this, "KubectlLayer"))
      .authenticationMode(AuthenticationMode.API)
      .outputClusterName(true)
      .endpointAccess(EndpointAccess.PUBLIC)
      .vpcSubnets(List.of(SubnetSelection.builder()
        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
        .build()))
      .build();
  }

  private void addFargateProfile()
  {
    FargateProfile profile = cluster.addFargateProfile("CustomerServiceProfile",
      FargateProfileOptions.builder()
        .fargateProfileName(config.eks().fargateProfileName())
        .selectors(List.of(Selector.builder()
          .namespace(config.eks().namespace())
          .build()))
        .subnetSelection(SubnetSelection.builder()
          .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
          .build())
        .vpc(cluster.getVpc())
        .build());
    profile.getPodExecutionRole().addManagedPolicy(
      ManagedPolicy.fromAwsManagedPolicyName("CloudWatchLogsFullAccess"));
  }

  private ServiceAccount setupServiceAccountWithIAM()
  {
    ServiceAccount sa = cluster.addServiceAccount("CustomerServiceAccount",
      ServiceAccountOptions.builder()
        .name(config.eks().serviceAccountName())
        .namespace(config.eks().namespace())
        .build());
    databaseStack.getDatabase().grantConnect(sa.getRole());
    Objects.requireNonNull(databaseStack.getDatabase().getSecret())
      .grantRead(sa.getRole());
    return sa;
  }

  @SuppressWarnings("unchecked")
  private KubernetesManifest addConfigMap()
  {
    return cluster.addManifest("CustomerServiceConfigMap", Map.of(
      "apiVersion", "v1",
      "kind", "ConfigMap",
      "metadata", Map.of(
        "name", config.eks().configMapName(),
        "namespace", config.eks().namespace()
      ),
      "data", Map.of(
        "QUARKUS_DATASOURCE_JDBC_URL",
        "jdbc:postgresql://" + databaseStack.getDatabase().getDbInstanceEndpointAddress() +
          ":5432/" + config.database().databaseName(),
        "QUARKUS_DATASOURCE_USERNAME", config.database().secretUsername(),
        "QUARKUS_REDIS_HOSTS",
        "redis://" + databaseStack.getRedis().getPrimaryEndpoint() + ":6379"
      )
    ));
  }

  @SuppressWarnings("unchecked")
  private KubernetesManifest createNamespace()
  {
    return cluster.addManifest("CustomerServiceNamespace", Map.of(
      "apiVersion", "v1",
      "kind", "Namespace",
      "metadata", Map.of("name", config.eks().namespace())
    ));
  }

  @SuppressWarnings("unchecked")
  private void addDeploymentAndService(KubernetesManifest configMap) throws IOException
  {
    List<Map<String, Object>> manifests = loadYamlManifests("k8s/customer-service.yaml");
    KubernetesManifest previous = configMap;
    for (int i = 0; i < manifests.size(); i++)
    {
      KubernetesManifest current =
        cluster.addManifest("CustomerService-%d".formatted(i), manifests.get(i));
      current.getNode().addDependency(previous);
      previous = current;
    }
  }

  private List<Map<String, Object>> loadYamlManifests(String path) throws IOException
  {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(path))
    {
      return StreamSupport.stream(new Yaml().loadAll(is).spliterator(), false)
        .map(o -> (Map<String, Object>) o)
        .filter(Objects::nonNull)
        .toList();
    }
  }
}
