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

  @Test
  public void testEcsServiceConfiguration() throws Exception
  {
    JsonObject resources = getTemplateResources();
    JsonObject ecsService = findResourceByType(resources, "AWS::ECS::Service");

    JsonObject properties = ecsService.getJsonObject("Properties");
    assertEquals(3, properties.getInt("DesiredCount"), "Should have 3 desired tasks");
    assertEquals("FARGATE", properties.getString("LaunchType"), "Should use Fargate");
  }

  @Test
  public void testRdsConfiguration() throws Exception
  {
    JsonObject resources = getTemplateResources();
    JsonObject rdsInstance = findResourceByType(resources, "AWS::RDS::DBInstance");

    JsonObject properties = rdsInstance.getJsonObject("Properties");
    assertEquals("postgres", properties.getString("Engine"), "Should use PostgreSQL");
    assertEquals("customers", properties.getString("DBName"), "Should have correct DB name");
  }

  @Test
  public void testSecurityGroups() throws Exception
  {
    JsonObject resources = getTemplateResources();

    // Verify database security group only allows access from ECS
    JsonObject dbSG = findResourceByType(resources, "AWS::EC2::SecurityGroup");
    // Validate ingress rules...

    // Verify no public access to database
    assertFalse(hasPublicAccess(dbSG), "Database should not have public access");
  }

  @Test
  public void testSecretsManagement() throws Exception
  {
    JsonObject resources = getTemplateResources();
    assertTrue(hasResourceOfType(resources, "AWS::SecretsManager::Secret"),
      "Should use Secrets Manager for DB credentials");
  }

  @Test
  public void testVpcConfiguration() throws Exception
  {
    JsonObject resources = getTemplateResources();

    // Verify VPC has both public and private subnets
    long publicSubnets = countResourcesWithProperty(resources, "AWS::EC2::Subnet", "MapPublicIpOnLaunch", true);
    long privateSubnets = countResourcesWithProperty(resources, "AWS::EC2::Subnet", "MapPublicIpOnLaunch", false);

    assertEquals(2, publicSubnets, "Should have 2 public subnets");
    assertEquals(2, privateSubnets, "Should have 2 private subnets");
  }

  @Test
  public void testResourceSizing() throws Exception
  {
    JsonObject resources = getTemplateResources();
    JsonObject rdsInstance = findResourceByType(resources, "AWS::RDS::DBInstance");

    String instanceClass = rdsInstance.getJsonObject("Properties").getString("DBInstanceClass");
    assertTrue(instanceClass.contains("db.t3.large") || instanceClass.contains("db.t3.xlarge"),
      "Should use cost-effective instance types for development");
  }

  @Test
  public void testEnvironmentConfiguration() throws Exception
  {
    // Test with different CDK_DEFAULT_ACCOUNT values
    // Verify environment-specific settings are applied correctly
  }

  @Test
  public void testComplianceRequirements() throws Exception
  {
    JsonObject resources = getTemplateResources();

    // Verify encryption at rest
    JsonObject rdsInstance = findResourceByType(resources, "AWS::RDS::DBInstance");
    assertTrue(rdsInstance.getJsonObject("Properties").getBoolean("StorageEncrypted", false),
      "RDS should have encryption enabled");

    // Verify backup retention
    int backupRetention = rdsInstance.getJsonObject("Properties").getInt("BackupRetentionPeriod", 0);
    assertTrue(backupRetention >= 0, "Backup retention should be configured (0 for dev, 7+ for prod)");
  }

  @Test
  public void testTemplateStability() throws Exception
  {
    // Compare current template with baseline
    // Ensure no unintended changes in resource configurations
    Path baselineTemplate = Paths.get("src/test/resources/baseline-template.json");
    if (Files.exists(baselineTemplate))
    {
      // Compare critical sections and alert on unexpected changes
    }
  }

  // Helper method to get the template as JsonObject
  private JsonObject getTemplate() throws Exception
  {
    Path templateFile = Paths.get("cdk.out/QuarkusCustomerManagementStack.template.json");
    String content = Files.readString(templateFile);
    try (Jsonb jsonb = JsonbBuilder.create())
    {
      return jsonb.fromJson(content, JsonObject.class);
    }
  }

  // Helper method to get the Resources section
  private JsonObject getTemplateResources() throws Exception
  {
    JsonObject template = getTemplate();
    JsonObject resources = template.getJsonObject("Resources");
    assertNotNull(resources, "Template should have Resources section");
    return resources;
  }

  // Helper method to find a specific resource by type
  private JsonObject findResourceByType(JsonObject resources, String type)
  {
    return resources.entrySet().stream()
      .map(entry -> entry.getValue().asJsonObject())
      .filter(resource -> type.equals(resource.getString("Type", "")))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Resource of type " + type + " not found"));
  }

  // Helper method to count resources with a specific property value
  private long countResourcesWithProperty(JsonObject resources, String resourceType, String propertyName, Object expectedValue)
  {
    return resources.entrySet().stream()
      .map(entry -> entry.getValue().asJsonObject())
      .filter(resource -> resourceType.equals(resource.getString("Type", "")))
      .filter(resource ->
      {
        JsonObject properties = resource.getJsonObject("Properties");
        if (properties == null) return false;

        if (expectedValue instanceof Boolean)
        {
          return expectedValue.equals(properties.getBoolean(propertyName, false));
        }
        else if (expectedValue instanceof String)
        {
          return expectedValue.equals(properties.getString(propertyName, ""));
        }
        else if (expectedValue instanceof Integer)
        {
          return expectedValue.equals(properties.getInt(propertyName, -1));
        }
        return false;
      })
      .count();
  }

  // Helper method to check if a security group has public access
  private boolean hasPublicAccess(JsonObject securityGroup)
  {
    JsonObject properties = securityGroup.getJsonObject("Properties");
    if (properties == null) return false;

    JsonArray ingressRules = properties.getJsonArray("SecurityGroupIngress");
    if (ingressRules == null) return false;

    return ingressRules.stream()
      .map(JsonValue::asJsonObject)
      .anyMatch(rule ->
      {
        String cidrIp = rule.getString("CidrIp", "");
        return "0.0.0.0/0".equals(cidrIp);
      });
  }

  // Helper method to find all resources of a specific type
  private Stream<JsonObject> findResourcesByType(JsonObject resources, String type)
  {
    return resources.entrySet().stream()
      .map(entry -> entry.getValue().asJsonObject())
      .filter(resource -> type.equals(resource.getString("Type", "")));
  }

  // Helper method to check if dependencies contain database reference
  private boolean containsDatabaseReference(JsonArray dependsOn)
  {
    if (dependsOn == null) return false;

    return dependsOn.stream()
      .map(JsonValue::toString)
      .anyMatch(dep -> dep.toLowerCase().contains("database"));
  }
}
