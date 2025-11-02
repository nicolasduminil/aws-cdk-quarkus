package fr.simplex_software.workshop.customer_service_ecs.infrastructure;

import jakarta.enterprise.context.*;
import jakarta.inject.*;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.*;
import software.amazon.awscdk.*;

@ApplicationScoped
public class CustomerManagementProducer
{
  @ConfigProperty(name = "cdk.account", defaultValue = "${CDK_DEFAULT_ACCOUNT}")
  String account;

  @ConfigProperty(name = "cdk.region", defaultValue = "${CDK_DEFAULT_REGION}")
  String region;

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
}
