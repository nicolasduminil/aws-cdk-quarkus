package fr.simplex_software.workshop.customer_service_eks.tests;

import fr.simplex_software.workshop.cdk.common.tests.*;
import org.junit.jupiter.api.*;

public class CustomerServiceE2EIT extends AbstractCustomerServiceE2E
{
  private static Process portForwardProcess;

  @BeforeAll
  static void setup() throws Exception
  {
    startPortForward();
    configureEndpoint("localhost:8080");
    waitForServiceReady();
  }

  @AfterAll
  static void teardown()
  {
    if (portForwardProcess != null && portForwardProcess.isAlive())
    {
      portForwardProcess.destroy();
      System.out.println(">>> Port-forward stopped");
    }
  }

  private static void startPortForward() throws Exception
  {
    System.out.println(">>> Waiting for deployment to be ready...");
    Process waitProcess = new ProcessBuilder(
      "kubectl", "wait", "--for=condition=Available",
      "deployment/customer-service-api-deployment",
      "-n", "customer-service",
      "--timeout=300s"
    ).start();

    if (waitProcess.waitFor() != 0)
      throw new RuntimeException("### Deployment not available");

    System.out.println(">>> Starting port-forward...");
    portForwardProcess = new ProcessBuilder(
      "kubectl", "port-forward",
      "deployment/customer-service-api-deployment",
      "8080:8080",
      "-n", "customer-service"
    ).start();

    Thread.sleep(3000);
    System.out.println(">>> Port-forward established on localhost:8080");
  }
}
