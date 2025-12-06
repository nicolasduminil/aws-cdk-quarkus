package fr.simplex_software.workshop.customer_service_ecs.cdk.tests;

import fr.simplex_software.workshop.cdk.common.tests.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.regions.*;
import software.amazon.awssdk.services.cloudformation.*;
import software.amazon.awssdk.services.cloudformation.model.*;

public class CustomerServiceE2EIT extends AbstractCustomerServiceE2E
{
  private static CloudFormationClient cfClient;

  @BeforeAll
  static void setup()
  {
    cfClient = CloudFormationClient.builder()
      .region(Region.EU_WEST_3)
      .build();
    String endpoint = getStackOutput("QuarkusCustomerManagementStack", "CustomerServiceLoadBalancerDNS");
    configureEndpoint(endpoint);
    waitForServiceReady();
  }

  private static String getStackOutput(String stackName, String outputKey)
  {
    try
    {
      DescribeStacksResponse response = cfClient.describeStacks(
        DescribeStacksRequest.builder().stackName(stackName).build());
      return response.stacks().getFirst().outputs().stream()
        .filter(output -> output.outputKey().equals(outputKey))
        .findFirst()
        .map(Output::outputValue)
        .orElseThrow(() -> new RuntimeException("Output " + outputKey + " not found"));
    }
    catch (Exception e)
    {
      throw new RuntimeException("Failed to get stack output: " + e.getMessage());
    }
  }
}
