package fr.simplex_software.workshop.customer_service_ecs.cdk.tests;

import io.restassured.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.regions.*;
import software.amazon.awssdk.services.cloudformation.*;
import software.amazon.awssdk.services.cloudformation.model.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class CustomerServiceE2EIT
{
  private static CloudFormationClient cfClient;
  private static String customerId;

  @BeforeAll
  static void setup()
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

  @Test
  @Order(2)
  void testHealthEndpoint()
  {
    given()
      .when()
      .get("/q/health")
      .then()
      .statusCode(200)
      .body("status", equalTo("UP"));
  }

  @Test
  @Order(3)
  void testCreateCustomer()
  {
    customerId = given()
      .contentType("application/json")
      .body("""
        {
            "firstName": "Integration",
            "lastName": "Test",
            "email": "integration.test@example.com",
            "phone": "+1-555-TEST",
            "address": "123 Test Street"
        }
        """)
      .when()
      .post("/customers")
      .then()
      .statusCode(201)
      .body("firstName", equalTo("Integration"))
      .body("lastName", equalTo("Test"))
      .body("email", equalTo("integration.test@example.com"))
      .extract()
      .path("id")
      .toString();
  }

  @Test
  @Order(4)
  void testGetCustomer()
  {
    given()
      .when()
      .get("/customers/" + customerId)
      .then()
      .statusCode(200)
      .body("firstName", equalTo("Integration"))
      .body("email", equalTo("integration.test@example.com"));
  }

  @Test
  @Order(5)
  void testListCustomers()
  {
    given()
      .when()
      .get("/customers")
      .then()
      .statusCode(200)
      .body("size()", greaterThan(0))
      .body("find { it.email == 'integration.test@example.com' }", notNullValue());
  }

  @Test
  @Order(6)
  void testUpdateCustomer()
  {
    given()
      .contentType("application/json")
      .body("""
        {
            "firstName": "Updated",
            "lastName": "Test",
            "email": "updated.test@example.com",
            "phone": "+1-555-UPDATED",
            "address": "456 Updated Street"
        }
        """)
      .when()
      .put("/customers/" + customerId)
      .then()
      .statusCode(200)
      .body("firstName", equalTo("Updated"))
      .body("email", equalTo("updated.test@example.com"));
  }

  @Test
  @Order(7)
  void testCachePerformance()
  {
    long start1 = System.currentTimeMillis();
    given().when().get("/customers/" + customerId).then().statusCode(200);
    long time1 = System.currentTimeMillis() - start1;
    long start2 = System.currentTimeMillis();
    given().when().get("/customers/" + customerId).then().statusCode(200);
    long time2 = System.currentTimeMillis() - start2;
    System.out.println("First call: " + time1 + "ms, Second call: " + time2 + "ms");
  }

  @Test
  @Order(8)
  void testDeleteCustomer()
  {
    given()
      .when()
      .delete("/customers/" + customerId)
      .then()
      .statusCode(204);
    given()
      .when()
      .get("/customers/" + customerId)
      .then()
      .statusCode(404);
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

  private static void waitForServiceReady()
  {
    System.out.println(">>> Waiting for service to be ready...");
    for (int i = 0; i < 30; i++)
      try
      {
        given().when().get("/q/health").then().statusCode(200);
        System.out.println(">>> Service is ready!");
        return;
      }
      catch (Exception e)
      {
        System.out.println("### Attempt " + (i + 1) + "/30 - Service not ready yet...");
        try
        {
          Thread.sleep(10000); // Wait 10 seconds
        }
        catch (InterruptedException ie)
        {
          Thread.currentThread().interrupt();
          throw new RuntimeException(ie);
        }
      }
    throw new RuntimeException("Service did not become ready within 5 minutes");
  }
}
