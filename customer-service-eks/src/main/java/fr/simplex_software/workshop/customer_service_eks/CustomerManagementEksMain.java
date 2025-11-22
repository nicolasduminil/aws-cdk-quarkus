package fr.simplex_software.workshop.customer_service_eks;

import io.quarkus.runtime.*;
import io.quarkus.runtime.annotations.*;

@QuarkusMain
public class CustomerManagementEksMain
{
  public static void main(String... args) {
    Quarkus.run(CustomerManagementEksApp.class, args);
  }
}
