package fr.simplex_software.workshop.customer_service_eks;

import fr.simplex_software.workshop.cdk.common.config.*;
import jakarta.annotation.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;

@ApplicationScoped
public class VpcStack extends Stack
{
  private Vpc vpc;
  private final InfrastructureConfig config;

  @Inject
  public VpcStack(App app, InfrastructureConfig config)
  {
    super(app, "VpcStack");
    this.config = config;
  }

  @PostConstruct
  public void initStack()
  {
    vpc = Vpc.Builder.create(this, "EksVpc")
      .maxAzs(config.vpc().maxAzs())
      .natGateways(config.vpc().natGateways())
      .build();
  }

  public Vpc getVpc() { return vpc; }
}
