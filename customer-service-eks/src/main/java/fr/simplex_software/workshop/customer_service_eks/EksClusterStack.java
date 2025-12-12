package fr.simplex_software.workshop.customer_service_eks;

import fr.simplex_software.workshop.cdk.common.config.*;
import fr.simplex_software.workshop.cdk.common.stacks.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.cdk.lambdalayer.kubectl.v32.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.eks.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.*;

import java.util.*;

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

  public void initStack()
  {
    createCluster();
    addFargateProfile();
    KubernetesManifest namespace = createNamespace();
    ServiceAccount serviceAccount = setupServiceAccountWithIAM();
    serviceAccount.getNode().addDependency(namespace);
    KubernetesManifest configMap = addConfigMap();
    configMap.getNode().addDependency(serviceAccount);
    addDeploymentAndService(configMap);
  }

  private void createCluster()
  {
    cluster = FargateCluster.Builder.create(this, "CustomerServiceCluster")
      .clusterName("customer-service-cluster")
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
    String namespace = "customer-service";

    FargateProfile profile = cluster.addFargateProfile("CustomerServiceProfile",
      FargateProfileOptions.builder()
        .fargateProfileName("customer-service-fargate-profile")
        .selectors(List.of(Selector.builder()
          .namespace(namespace)
          .build()))
        .subnetSelection(SubnetSelection.builder()
          .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
          .build())
        .vpc(cluster.getVpc())
        .build());

    IManagedPolicy loggingPolicy = ManagedPolicy.Builder.create(this, "FargateLoggingPolicy")
      .statements(List.of(PolicyStatement.Builder.create()
        .effect(Effect.ALLOW)
        .actions(List.of("logs:CreateLogStream", "logs:CreateLogGroup",
          "logs:DescribeLogStreams", "logs:PutLogEvents"))
        .resources(List.of("*"))
        .build()))
      .build();

    profile.getPodExecutionRole().addManagedPolicy(loggingPolicy);

    profile.getPodExecutionRole().addManagedPolicy(
      ManagedPolicy.fromAwsManagedPolicyName("AmazonEC2ContainerRegistryReadOnly")
    );
  }

  private ServiceAccount setupServiceAccountWithIAM()
  {
    String namespace = "customer-service";
    String serviceAccountName = "sa-customer-service";

    CfnJson conditions = CfnJson.Builder.create(this, "ConditionJson")
      .value(Map.of(
        cluster.getClusterOpenIdConnectIssuer() + ":aud", "sts.amazonaws.com",
        cluster.getClusterOpenIdConnectIssuer() + ":sub",
        "system:serviceaccount:" + namespace + ":" + serviceAccountName
      ))
      .build();

    FederatedPrincipal principal = new FederatedPrincipal(
      cluster.getOpenIdConnectProvider().getOpenIdConnectProviderArn(),
      Map.of("StringEquals", conditions),
      "sts:AssumeRoleWithWebIdentity"
    );

    Role serviceAccountRole = Role.Builder.create(this, "ServiceAccountRole")
      .assumedBy(principal)
      .build();

    databaseStack.getDatabase().grantConnect(serviceAccountRole);
    Objects.requireNonNull(databaseStack.getDatabase().getSecret()).grantRead(serviceAccountRole);

    serviceAccountRole.addToPolicy(PolicyStatement.Builder.create()
      .effect(Effect.ALLOW)
      .actions(List.of("elasticache:DescribeCacheClusters"))
      .resources(List.of("*"))
      .build());

    return cluster.addServiceAccount("CustomerServiceAccount", ServiceAccountOptions.builder()
      .name(serviceAccountName)
      .namespace(namespace)
      .annotations(Map.of("eks.amazonaws.com/role-arn", serviceAccountRole.getRoleArn()))
      .build());
  }

  @SuppressWarnings("unchecked")
  private KubernetesManifest addConfigMap()
  {
    return cluster.addManifest("CustomerServiceConfigMap", Map.of(
      "apiVersion", "v1",
      "kind", "ConfigMap",
      "metadata", Map.of(
        "name", "customer-service-config",
        "namespace", "customer-service"
      ),
      "data", Map.of(
        "QUARKUS_DATASOURCE_JDBC_URL",
        "jdbc:postgresql://" + databaseStack.getDatabase().getDbInstanceEndpointAddress() + ":5432/customers",
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
      "metadata", Map.of("name", "customer-service")
    ));
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

  @SuppressWarnings("unchecked")
  private void addDeploymentAndService(KubernetesManifest configMap)  // New method
  {
    String account = System.getenv("CDK_DEFAULT_ACCOUNT");
    String region = System.getenv("CDK_DEFAULT_REGION");
    String imageUrl = account + ".dkr.ecr." + region + ".amazonaws.com/nicolasduminil/customers-api:latest";

    System.out.println("=== DEBUG INFO ===");
    System.out.println("CDK_DEFAULT_ACCOUNT: " + account);
    System.out.println("CDK_DEFAULT_REGION: " + region);
    System.out.println("Image URL: " + imageUrl);
    System.out.println("==================");

    KubernetesManifest deployment = cluster.addManifest("CustomerServiceDeployment", Map.of(
      "apiVersion", "apps/v1",
      "kind", "Deployment",
      "metadata", Map.of(
        "name", "customer-service-api-deployment",
        "namespace", "customer-service"
      ),
      "spec", Map.of(
        "replicas", 2,
        "selector", Map.of("matchLabels", Map.of("app", "customer-service-api")),
        "template", Map.of(
          "metadata", Map.of("labels", Map.of("app", "customer-service-api")),
          "spec", Map.of(
            "serviceAccountName", "sa-customer-service",
            "containers", List.of(Map.of(
              "name", "customer-service-api-container",
              "image", imageUrl,
              "ports", List.of(Map.of("containerPort", 8080)),
              "envFrom", List.of(
                Map.of("configMapRef", Map.of("name", "customer-service-config")),
                Map.of("secretRef", Map.of("name", "db-credentials"))
              )
            ))
          )
        )
      )
    ));

    KubernetesManifest service = cluster.addManifest("CustomerServiceApiService", Map.of(
      "apiVersion", "v1",
      "kind", "Service",
      "metadata", Map.of(
        "name", "customer-service-api-service",
        "namespace", "customer-service",
        "annotations", Map.of(
          "service.beta.kubernetes.io/aws-load-balancer-type", "external",
          "service.beta.kubernetes.io/aws-load-balancer-nlb-target-type", "ip",
          "service.beta.kubernetes.io/aws-load-balancer-scheme", "internet-facing"
        )
      ),
      "spec", Map.of(
        "selector", Map.of("app", "customer-service-api"),
        "ports", List.of(Map.of("port", 80, "targetPort", 8080, "protocol", "TCP")),
        "type", "LoadBalancer"
      )
    ));

    // Set dependencies for both
    deployment.getNode().addDependency(configMap);
    service.getNode().addDependency(deployment);
  }
}
