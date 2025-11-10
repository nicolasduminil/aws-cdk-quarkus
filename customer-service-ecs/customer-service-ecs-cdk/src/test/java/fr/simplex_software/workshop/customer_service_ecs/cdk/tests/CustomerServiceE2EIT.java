package fr.simplex_software.workshop.customer_service_ecs.cdk.tests;

import io.restassured.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.regions.*;
import software.amazon.awssdk.services.cloudformation.*;
import software.amazon.awssdk.services.cloudformation.model.*;

import static io.restassured.RestAssured.*;
import static org.junit.jupiter.api.Assertions.*;

public class CustomerServiceE2EIT extends InfrastructureIT
{
  @BeforeAll
  void setup()
  {
    cfClient = CloudFormationClient.builder()
      .region(Region.EU_WEST_3)
      .build();
    String loadBalancerUrl = getStackOutput("QuarkusCustomerManagementStack", "CustomerServiceLoadBalancerDNS");
    RestAssured.baseURI = "http://" + loadBalancerUrl;
    RestAssured.port = 80;
    System.out.println(">>> Connecting to: " + RestAssured.baseURI + ":" + RestAssured.port);
    waitForServiceReady();
  }
}
