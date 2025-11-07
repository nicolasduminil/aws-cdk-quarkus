package fr.simplex_software.workshop.customer_service_ecs.cdk;

import io.quarkus.runtime.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import software.amazon.awscdk.*;

@ApplicationScoped
public class CustomerManagementApp implements QuarkusApplication
{
  private CustomerManagementStack customerManagementStack;
  private App app;

  @Inject
  public CustomerManagementApp (App app, CustomerManagementStack customerManagementStack)
  {
    this.app = app;
    this.customerManagementStack = customerManagementStack;
  }

  @Override
  public int run(String... args) throws Exception
  {
    Tags.of(app).add("project", "Containerized Customer Management Application on ECS/Fargate");
    Tags.of(app).add("environment", "development");
    Tags.of(app).add("application", "CustomerManagementApp");
    customerManagementStack.initStack();
    app.synth();
    return 0;
  }
}
