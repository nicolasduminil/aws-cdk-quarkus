package fr.simplex_software.workshop.customer_service_eks;

import io.quarkus.runtime.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import software.amazon.awscdk.*;

@ApplicationScoped
public class CustomerManagementEksApp implements QuarkusApplication
{
  @Inject
  App app;
  @Inject
  EksClusterStack eksClusterStack;
  @Inject
  CiCdPipelineStack ciCdPipelineStack;
  @Inject
  MonitoringStack monitoringStack;

  @Override
  public int run(String... args) throws Exception
  {
    Tags.of(app).add("project", "Customer Management on EKS with CI/CD");
    Tags.of(app).add("environment", "development");

    eksClusterStack.initStack();
    ciCdPipelineStack.initStack();
    monitoringStack.initStack();

    app.synth();
    return 0;
  }
}
