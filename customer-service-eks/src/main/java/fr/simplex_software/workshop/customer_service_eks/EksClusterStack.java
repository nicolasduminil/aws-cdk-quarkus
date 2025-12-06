package fr.simplex_software.workshop.customer_service_eks;

import fr.simplex_software.workshop.cdk.common.config.*;
import fr.simplex_software.workshop.cdk.common.stacks.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.cdk.lambdalayer.kubectl.v32.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.eks.*;
import software.amazon.awscdk.services.iam.*;

import java.util.*;

@ApplicationScoped
public class EksClusterStack extends Stack
{
  private Cluster cluster;
  private final Vpc vpc;
  private final DatabaseStack databaseStack;
  private final InfrastructureConfig config;
  private static final String CDK_BUILT_IN_ACCOUNT = "arn:aws:iam::%s:role/cdk-*-cfn-exec-role-*";

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
    cluster = Cluster.Builder.create(this, "CustomerServiceCluster")
      .clusterName("customer-service-cluster")
      .version(KubernetesVersion.V1_34)
      .vpc(vpc)
      .kubectlLayer(new KubectlV32Layer(this, "KubectlLayer"))
      .defaultCapacity(0)
      .mastersRole(Role.fromRoleArn(this, "AdminRole",
        CDK_BUILT_IN_ACCOUNT.formatted(Stack.of(this).getAccount())))
      .build();

    cluster.addFargateProfile("CustomerServiceProfile",
      FargateProfileOptions.builder()
        .fargateProfileName("customer-service-fargate-profile")
        .selectors(List.of(Selector.builder()
          .namespace("customer-service")
          .build()))
        .build());
  }

  public Cluster getCluster()
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
}
