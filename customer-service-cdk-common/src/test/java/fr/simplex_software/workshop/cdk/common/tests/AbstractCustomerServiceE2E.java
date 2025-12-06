package fr.simplex_software.workshop.cdk.common.tests;

import io.restassured.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractCustomerServiceE2E
{
  protected static String customerId;

  protected static void configureEndpoint(String endpoint)
  {
    RestAssured.baseURI = "http://%s".formatted(endpoint);
    RestAssured.port = 80;
    System.out.println(">>> Connecting to: %s:%d"
      .formatted(RestAssured.baseURI, RestAssured.port));
  }

  @Test
  @Order(1)
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
  @Order(2)
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
  @Order(3)
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
  @Order(4)
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
  @Order(5)
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
  @Order(6)
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
  @Order(7)
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

  protected static void waitForServiceReady()
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
          Thread.sleep(10000);
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
