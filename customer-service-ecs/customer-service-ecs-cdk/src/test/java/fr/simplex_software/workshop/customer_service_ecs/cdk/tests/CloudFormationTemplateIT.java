package fr.simplex_software.workshop.customer_service_ecs.cdk.tests;

import jakarta.json.*;
import jakarta.json.bind.*;
import org.junit.jupiter.api.*;


import java.io.*;
import java.nio.file.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class CloudFormationTemplateIT
{
  @Test
  public void testCloudFormationTemplatesGenerated() throws IOException
  {
    Path cdkOutDir = Paths.get("cdk.out");
    assertTrue(Files.exists(cdkOutDir), "CDK output directory should exist");
    Path templateFile = cdkOutDir.resolve("QuarkusCustomerManagementStack.template.json");
    assertTrue(Files.exists(templateFile), "CloudFormation template should be generated");
    String content = Files.readString(templateFile);
    assertFalse(content.isEmpty(), "Template should not be empty");
    assertTrue(content.contains("Resources"), "Should be valid CloudFormation template");
  }

  @Test
  public void testRequiredResourcesInTemplate() throws Exception
  {
    Path templateFile = Paths.get("cdk.out/QuarkusCustomerManagementStack.template.json");
    String content = Files.readString(templateFile);
    try (Jsonb jsonb = JsonbBuilder.create())
    {
      JsonObject template = jsonb.fromJson(content, JsonObject.class);
      JsonObject resources = template.getJsonObject("Resources");
      assertNotNull(resources, "Template should have Resources section");
      assertTrue(hasResourceOfType(resources, "AWS::ECS::Service"), "Should have ECS Service");
      assertTrue(hasResourceOfType(resources, "AWS::RDS::DBInstance"), "Should have RDS instance");
      assertTrue(hasResourceOfType(resources, "AWS::ElastiCache::ReplicationGroup"), "Should have Redis");
    }
  }

  private boolean hasResourceOfType(JsonObject resources, String type)
  {
    return resources.entrySet().stream()
      .anyMatch(entry ->
      {
        JsonObject resource = entry.getValue().asJsonObject();
        return type.equals(resource.getString("Type", ""));
      });
  }

  @Test
  public void testCdkSynthSucceeded() throws IOException
  {
    Path cdkOut = Paths.get("cdk.out");
    assertTrue(Files.exists(cdkOut), "CDK synth should have created output directory");
    try (Stream<Path> files = Files.list(cdkOut))
    {
      long templateCount = files.filter(p -> p.toString().endsWith(".template.json")).count();
      assertTrue(templateCount > 0, "Should have generated at least one CloudFormation template");
    }
  }
}
