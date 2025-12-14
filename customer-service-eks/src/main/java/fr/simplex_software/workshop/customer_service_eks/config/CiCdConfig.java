package fr.simplex_software.workshop.customer_service_eks.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "cdk.cicd")
public interface CiCdConfig
{
  RepositoryConfig repository();
  GitHubConfig github();
  BuildConfig build();
  PipelineConfig pipeline();

  interface RepositoryConfig
  {
    @WithDefault("customer-service")
    String name();
  }

  interface GitHubConfig
  {
    @WithDefault("your-github-user")
    String owner();
    @WithDefault("customer-service")
    String repo();
    @WithDefault("github-token")
    String tokenSecret();
  }

  interface BuildConfig
  {
    @WithDefault("src/main/resources/buildspecs/build-spec.yaml")
    String buildSpecPath();
    @WithDefault("src/main/resources/buildspecs/deploy-spec.yaml")
    String deploySpecPath();
  }

  interface PipelineConfig
  {
    @WithDefault("CustomerServicePipeline")
    String name();
    StageNames stages();
    ActionNames actions();
    interface StageNames
    {
      @WithDefault("Source")
      String source();
      @WithDefault("Build")
      String build();
      @WithDefault("Deploy")
      String deploy();
    }

    interface ActionNames
    {
      @WithDefault("GitHub_Source")
      String source();
      @WithDefault("Build")
      String build();
      @WithDefault("Deploy")
      String deploy();
    }
  }
}
