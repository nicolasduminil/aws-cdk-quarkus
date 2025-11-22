package fr.simplex_software.workshop.customer_service_eks;

import fr.simplex_software.workshop.cdk.common.config.*;
import fr.simplex_software.workshop.customer_service_eks.config.*;
import jakarta.enterprise.context.*;
import jakarta.inject.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import software.amazon.awscdk.services.ecr.*;

import java.util.*;

@ApplicationScoped
public class CiCdPipelineStack extends Stack
{
  private final EksClusterStack eksStack;
  private final CiCdConfig cicdConfig;

  @Inject
  public CiCdPipelineStack(App app, EksClusterStack eksStack,
    InfrastructureConfig config, CiCdConfig cicdConfig)
  {
    super(app, "CiCdPipelineStack-Test");
    this.eksStack = eksStack;
    this.cicdConfig = cicdConfig;
    addDependency(eksStack);
  }


  public void initStack()
  {
    Repository ecrRepo = (Repository) Repository.fromRepositoryName(this, "CustomerServiceRepo",
      cicdConfig.repository().name());

    Project buildProject = Project.Builder.create(this, "CustomerServiceBuild")
      .source(Source.gitHub(GitHubSourceProps.builder()
        .owner(cicdConfig.github().owner())
        .repo(cicdConfig.github().repo())
        .build()))
      .environment(BuildEnvironment.builder()
        .buildImage(LinuxBuildImage.STANDARD_7_0)
        .privileged(true)
        .build())
      .buildSpec(BuildSpec.fromAsset(cicdConfig.build().buildSpecPath()))
      .build();


    Project deployProject = Project.Builder.create(this, "CustomerServiceDeploy")
      .environment(BuildEnvironment.builder()
        .buildImage(LinuxBuildImage.STANDARD_7_0)
        .environmentVariables(Map.of(
          "CLUSTER_NAME", BuildEnvironmentVariable.builder()
            .value(eksStack.getCluster().getClusterName())
            .build()
        ))
        .build())
      .buildSpec(BuildSpec.fromAsset(cicdConfig.build().deploySpecPath()))
      .build();

    GitHubSourceAction sourceAction = GitHubSourceAction.Builder.create()
      .actionName(cicdConfig.pipeline().actions().source())
      .owner(cicdConfig.github().owner())
      .repo(cicdConfig.github().repo())
      .oauthToken(SecretValue.secretsManager(cicdConfig.github().tokenSecret()))
      .output(Artifact.artifact("source"))
      .build();

    CodeBuildAction buildAction = CodeBuildAction.Builder.create()
      .actionName(cicdConfig.pipeline().actions().build())
      .project(buildProject)
      .input(Artifact.artifact("source"))
      .outputs(List.of(Artifact.artifact("build")))
      .build();

    CodeBuildAction deployAction = CodeBuildAction.Builder.create()
      .actionName(cicdConfig.pipeline().actions().deploy())
      .project(deployProject)
      .input(Artifact.artifact("source"))
      .environmentVariables(Map.of(
        "IMAGE_URI", BuildEnvironmentVariable.builder()
          .value(ecrRepo.getRepositoryUri())
          .build()
      ))
      .build();

    Pipeline pipeline = Pipeline.Builder.create(this, cicdConfig.pipeline().name())
      .build();

    pipeline.addStage(StageOptions.builder()
      .stageName(cicdConfig.pipeline().stages().source())
      .actions(List.of(sourceAction))
      .build());

    pipeline.addStage(StageOptions.builder()
      .stageName(cicdConfig.pipeline().stages().build())
      .actions(List.of(buildAction))
      .build());

    pipeline.addStage(StageOptions.builder()
      .stageName(cicdConfig.pipeline().stages().deploy())
      .actions(List.of(deployAction))
      .build());
  }
}
