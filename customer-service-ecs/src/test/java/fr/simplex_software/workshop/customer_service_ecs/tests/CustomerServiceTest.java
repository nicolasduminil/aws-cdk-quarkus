package fr.simplex_software.workshop.customer_service_ecs.tests;

import io.quarkus.test.junit.*;
import io.restassured.http.*;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class CustomerServiceTest
{
  @Test
  void testCreateCustomer()
  {
    given()
      .contentType(ContentType.JSON)
      .body("""
        {
            "firstName": "John",
            "lastName": "Doe", 
            "email": "john@example.com"
        }
        """)
      .when()
      .post("/customers")
      .then()
      .statusCode(201)
      .body("firstName", equalTo("John"));
  }
}
