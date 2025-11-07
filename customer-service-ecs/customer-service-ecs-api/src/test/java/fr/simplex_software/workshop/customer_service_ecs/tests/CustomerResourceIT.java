package fr.simplex_software.workshop.customer_service_ecs.tests;

import fr.simplex_software.workshop.customer_service_ecs.client.*;
import fr.simplex_software.workshop.customer_service_ecs.entity.*;
import io.quarkus.test.junit.*;
import jakarta.inject.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.*;
import org.eclipse.microprofile.rest.client.inject.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
public class CustomerResourceIT
{
  @Inject
  @RestClient
  CustomerClient customerClient;

  @Test
  void testCreateCustomer()
  {
    Customer customer = new Customer("John", "Doe", "john@example.com",
      "000000000000", "123 Main St");
    Response response = customerClient.createCustomer(customer);
    assertThat(response.getStatus()).isEqualTo(201);
    customer = response.readEntity(Customer.class);
    assertThat(customer.firstName).isEqualTo("John");
    assertThat(customer.lastName).isEqualTo("Doe");
    assertThat(customer.email).isEqualTo("john@example.com");
  }
}
