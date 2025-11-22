package fr.simplex_software.workshop.customer_service_api.api;

import fr.simplex_software.workshop.customer_service_api.entity.*;
import fr.simplex_software.workshop.customer_service_api.service.*;
import jakarta.inject.*;
import jakarta.validation.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.*;

@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerResource
{
  @Inject
  CustomerService customerService;

  @GET
  public Response getAll()
  {
    return Response.ok().
      entity(new GenericEntity<>(customerService.findAll()){}).build();
  }

  @GET
  @Path("/{id}")
  public Response get(@PathParam("id") Long id)
  {
    Customer customer = customerService.findById(id);
    return customer != null ?
      Response.ok(customer).build() :
      Response.status(Response.Status.NOT_FOUND).build();
  }

  @POST
  public Response create(@Valid Customer customer)
  {
    return Response.status(Response.Status.CREATED)
      .entity(customerService.create(customer)).build();
  }

  @PUT
  @Path("/{id}")
  public Response update(@PathParam("id") Long id, @Valid Customer customer)
  {
    Customer updated = customerService.update(id, customer);
    return updated != null ?
      Response.ok(updated).build() :
      Response.status(Response.Status.NOT_FOUND).build();
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id)
  {
    return customerService.delete(id) ?
      Response.noContent().build() :
      Response.status(Response.Status.NOT_FOUND).build();
  }
}
