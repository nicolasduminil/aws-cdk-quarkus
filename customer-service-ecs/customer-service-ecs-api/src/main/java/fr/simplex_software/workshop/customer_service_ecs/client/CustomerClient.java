package fr.simplex_software.workshop.customer_service_ecs.client;

import fr.simplex_software.workshop.customer_service_ecs.entity.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.rest.client.inject.*;

@RegisterRestClient(configKey = "customers-api")
@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CustomerClient
{
  @POST
  Response createCustomer(Customer customer);

  @GET
  @Path("/{id}")
  Response getCustomer(@PathParam("id") Long id);
}
