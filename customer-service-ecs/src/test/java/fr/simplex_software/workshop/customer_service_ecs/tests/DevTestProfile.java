package fr.simplex_software.workshop.customer_service_ecs.tests;

import io.quarkus.test.junit.*;

@TestProfile
public class DevTestProfile implements QuarkusTestProfile
{
  @Override
  public String getConfigProfile()
  {
    return "dev";
  }
}
