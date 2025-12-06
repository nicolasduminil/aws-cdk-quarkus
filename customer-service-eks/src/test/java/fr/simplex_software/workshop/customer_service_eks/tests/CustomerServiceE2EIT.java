package fr.simplex_software.workshop.customer_service_eks.tests;

import fr.simplex_software.workshop.cdk.common.tests.*;
import org.junit.jupiter.api.*;

public class CustomerServiceE2EIT extends AbstractCustomerServiceE2E
{
  @BeforeAll
  static void setup()
  {
    String endpoint = getKubernetesLoadBalancer();
    configureEndpoint(endpoint);
    waitForServiceReady();
  }

  private static String getKubernetesLoadBalancer()
  {
    try
    {
      System.out.println(">>> Waiting for service to be created...");
      Process waitProcess = new ProcessBuilder(
        "kubectl", "wait", "--for=condition=Available",
        "service/customer-service-api-service",
        "-n", "customer-service",
        "--timeout=300s"
      ).inheritIO().start();
      waitProcess.waitFor();
      System.out.println(">>> Waiting for LoadBalancer hostname...");
      for (int i = 0; i < 60; i++)
      {
        Process process = new ProcessBuilder(
          "kubectl", "get", "service", "customer-service-api-service",
          "-n", "customer-service",
          "-o", "jsonpath={.status.loadBalancer.ingress[0].hostname}"
        ).start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor();
        if (!output.isEmpty())
        {
          System.out.println(">>> LoadBalancer ready: " + output);
          return output;
        }
        System.out.println(">>> Attempt " + (i + 1) + "/60 - LoadBalancer not ready yet...");
        Thread.sleep(5000);
      }
      throw new RuntimeException("### LoadBalancer hostname not available after 5 minutes");
    }
    catch (Exception e)
    {
      throw new RuntimeException("### Failed to execute kubectl: " + e.getMessage());
    }
  }
}

