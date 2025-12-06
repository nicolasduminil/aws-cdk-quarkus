package fr.simplex_software.workshop.customer_service_api.config;

import io.quarkus.runtime.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.event.*;
import jakarta.json.bind.*;
import org.eclipse.microprofile.config.inject.*;
import org.slf4j.*;
import software.amazon.awssdk.regions.*;
import software.amazon.awssdk.services.secretsmanager.*;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.*;

@ApplicationScoped
public class SecretManagerConfig
{
  private static final Logger LOG = LoggerFactory.getLogger(SecretManagerConfig.class.getName());

  @ConfigProperty(name = "db.secret.arn")
  Optional<String> secretArn;
  @ConfigProperty(name = "aws.region")
  Optional<String> awsRegion;

  void onStart(@Observes StartupEvent ev)
  {
    if (secretArn.isEmpty() || awsRegion.isEmpty())
    {
      LOG.info("### AWS Secrets Manager not configured - using standard datasource configuration");
      return;
    }
    LOG.info(">>> Fetching database credentials from AWS Secrets Manager");
    try (SecretsManagerClient client = SecretsManagerClient.builder()
      .region(Region.of(awsRegion.get()))
      .build())
    {
      GetSecretValueResponse response = client.getSecretValue(
        GetSecretValueRequest.builder()
          .secretId(secretArn.get())
          .build());
      Map<String, String> credentials = JsonbBuilder.create().fromJson(response.secretString(), Map.class);
      System.setProperty("quarkus.datasource.username", credentials.get("username"));
      System.setProperty("quarkus.datasource.password", credentials.get("password"));
      LOG.info(">>> Successfully configured datasource with AWS Secrets Manager credentials");
    }
    catch (Exception e)
    {
      throw new RuntimeException("### Failed to retrieve database credentials from Secrets Manager", e);
    }
  }
}
