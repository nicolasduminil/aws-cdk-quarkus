package fr.simplex_software.workshop.customer_service_api.client;

import fr.simplex_software.workshop.customer_service_api.entity.*;
import jakarta.validation.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.Path;
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
  @GET
  public Response getCustomers();
  @PUT
  @Path("/{id}")
  Response updateCustomer(@PathParam("id") Long id, @Valid Customer customer);
  @DELETE
  @Path("/{id}")
  Response delete(@PathParam("id") Long id);
}
