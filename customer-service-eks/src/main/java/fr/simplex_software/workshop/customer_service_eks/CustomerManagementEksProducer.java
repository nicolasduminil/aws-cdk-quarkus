package fr.simplex_software.workshop.customer_service_eks;

import fr.simplex_software.workshop.cdk.common.config.*;
import fr.simplex_software.workshop.cdk.common.stacks.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.inject.*;
import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;

@ApplicationScoped
public class CustomerManagementEksProducer
{
  @ConfigProperty(name = "CDK_DEFAULT_ACCOUNT")
  String account;

  @ConfigProperty(name = "CDK_DEFAULT_REGION")
  String region;

  @Inject
  InfrastructureConfig config;

  @Produces
  @Singleton
  public App app()
  {
    return new App();
  }

  @Produces
  @Singleton
  public StackProps stackProps()
  {
    return StackProps.builder().env(Environment.builder().account(account)
      .region(region).build()).build();
  }

  @Produces
  @Dependent
  public Vpc produceVpc(VpcStack vpcStack)
  {
    return vpcStack.getVpc();
  }

  @Produces
  @ApplicationScoped
  public DatabaseStack produceDatabaseStack(App app, Vpc vpc)
  {
    return new DatabaseStack(app, "DatabaseStack", vpc, config);
  }
}
