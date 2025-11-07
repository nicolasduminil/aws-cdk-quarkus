package fr.simplex_software.workshop.customer_service_ecs.cdk;

import io.quarkus.runtime.*;
import io.quarkus.runtime.annotations.*;

@QuarkusMain
public class CustomerManagementMain
{
  public static void main(String... args)
  {
    Quarkus.run(CustomerManagementApp.class, args);
  }
}
