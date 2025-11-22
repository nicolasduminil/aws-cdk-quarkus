package fr.simplex_software.workshop.customer_service_eks;

import jakarta.enterprise.context.*;
import jakarta.enterprise.inject.*;
import jakarta.inject.*;
import org.eclipse.microprofile.config.inject.*;
import software.amazon.awscdk.*;

@ApplicationScoped
public class CustomerManagementEksProducer
{
  @ConfigProperty(name = "CDK_DEFAULT_ACCOUNT")
  String account;

  @ConfigProperty(name = "CDK_DEFAULT_REGION")
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
