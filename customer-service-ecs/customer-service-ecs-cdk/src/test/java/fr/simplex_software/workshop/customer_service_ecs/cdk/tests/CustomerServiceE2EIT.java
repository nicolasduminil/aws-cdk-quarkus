package fr.simplex_software.workshop.customer_service_ecs.cdk.tests;

import io.restassured.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.regions.*;
import software.amazon.awssdk.services.cloudformation.*;

public class CustomerServiceE2EIT extends InfrastructureIT
{
  @BeforeAll
  static void setupE2E()
  {
    cfClient = CloudFormationClient.builder()
      .region(Region.EU_WEST_3)
      .build();
    String loadBalancerUrl = getStackOutput("QuarkusCustomerManagementStack", "CustomerServiceLoadBalancerDNS");
    RestAssured.baseURI = "http://" + loadBalancerUrl;
    RestAssured.port = 80;
    waitForServiceReady();
  }
}
