# Building a Containerized Quarkus API on AWS EKS/Fargate with CDK

In a recent [post](http://www.simplex-software.fr/posts-archive/customer-service-ecs/), I have demonstrated the benefits
of using AWS ECS (*Elastic Container Service*), with Quarkus and the CDK (*Cloud Development Kit*), in order to implement
an API for the customer management. 

In the continuity of this previous post, the current one will try to go a bit further and replace ECS by EKS (*Elastic 
Kubernetes Service*) as the environment for running containerized workloads. Additionally, an automated CI/CD pipeline, 
using AWS CodePipeline and AWS CodeBuild, is provided.

## Architecture Overview

The solution that you're about to look at implements a complete production-ready architecture consisting of:

- **Presentation Layer**: A Quarkus REST API with OpenAPI/Swagger implementing the customer management solution. This implementation is exactly the same used in the previous project which leverages ECS.
- **Application Layer**: Business logic with Quarkus Panache for data access
- **Data Layer**: PostgreSQL (RDS) for persistence, Redis (ElastiCache) for caching
- **Container Orchestration**: AWS EKS with Fargate for serverless container execution
- **Infrastructure as Code**: AWS CDK implemented in Quarkus
- **CI/CD**: Automated pipeline with AWS CodePipeline, CodeBuild, and GitHub integration

Before starting, a couple of explanations are probably required. As you probably know, EKS can be used with two compute 
engines: EC2 or Fargate. In this example we've chosen to use Fargate, as it was also the case of our previous, ECS-based  
project.

Fargate is a serverless compute engine for containers that provisions and manages the underlying infrastructure and 
provides automatic scaling. It is designed to make it easy to run containers without having to manage servers or 
clusters. It's a great fit for workloads that don't have long-running connections or require frequent scaling. This project 
uses Fargate because it needs a continuously running containerized application. Fargate provides the serverless operational
model (no server management) while maintaining the traditional container execution model your Quarkus API requires.

The figure below shows the project's architecture diagram:

![Architecture Diagram](./architecture-diagram.png)

Please notice that, as mentioned above, several layers like: presentation, application and data are the same ones used in
the previous ECS-based example. Hence, we created a new module, called `customer-service-eks`, in the current Maven multi-module
project. This module is similar to the `customer-service-ecs` one and they both share the same presentation, application
and data layers, that have been moved in a shared Maven module, called `customer-service-cdk-common`.

## Prerequisites

The following prerequisites are required to run this project:

  - Java 21+
  - Maven 3.9+
  - Docker
  - AWS CLI installed and configured with appropriate credentials
  - kubectl installed
  - AWS CDK CLI installed
  - GitHub account with OAuth token stored in AWS Secrets Manager

## Project Structure

The project is structured as follows:

    customer-service-eks/
    ├── src/main/java/
    │ └── fr/simplex_software/workshop/customer_service_eks/
        │ └── config/
          │ └── CiCdConfig.java # CI/CD Pipeline configuration
        │ ├── CiCdPipelineStack.java # CDK Quarkus CI/CD pipeline infrastructure
        │ ├── CustomerManagementEksApp.java # Quarkus CDK application
        │ ├── CustomerManagementEksMain.java # Quarkus main application
        │ ├── CustomerManagementEksProducer.java # Quarkus CDI producer
        │ ├── EksClusterStack.java # Quarkus CDK EKS cluster infrastructure
        │ ├── MonitoringStack.java # Quarkus CDK monitoring stack infrastructure
        │ ├── VpcStack.java # Quarkus CDK VPC stack infrastructure
    ├── src/main/resources/
    │ ├── buildspecs/
    │ │ ├── build-spec.yaml # CodeBuild build specification
    │ │ └── deploy-spec.yaml # CodeBuild deploy specification
    │ ├── k8s/
    │ │ └── customer-service.yaml # Kubernetes manifests
    │ ├── scripts/  #several shell scripts
          ...
    │ └── application.properties # Configuration
    └── src/test/java/
        └── fr/simplex_software/workshop/customer_service_eks/tests/
            └── CustomerServiceE2EIT.java # End-to-end integration tests


## Configuration

The project's configuration is stored in two files:

  - the `env.properties` file
  - the `src/main/resources/application.properties`file.

The `env.properties` file contains environment variables that are used by the Maven build process. Its structure is 
reproduced below:

    CONTAINER_IMAGE_GROUP=nicolasduminil
    CONTAINER_IMAGE_NAME=customers-api
    CDK_DEFAULT_ACCOUNT=...
    CDK_DEFAULT_REGION=eu-west-3
    CDK_DEFAULT_USER=nicolas

The properties `CONTAINER_IMAGE_GROUP` and `CONTAINER_IMAGE_NAME` are used to build the container image and push it to 
the ECR repository. They are used by the JIB Quarkus extension to build the container image. The other properties are 
used by the CDK application to deploy the infrastructure and their meanings don't require any explicit explanation.

The project uses AWS Secrets Manager to store sensitive data like GitHub OAuth token which is used by the CI/CD pipeline.
In order to create the secret, you can use the script `setup-github-token.sh` reproduced below:

    #!/bin/bash
    set -e
    echo "=== GitHub Token Setup for CI/CD Pipeline ==="

    # Read token from stdin or argument
    if [ $# -eq 0 ]; then
      if [ -t 0 ]; then
        # No arguments and no piped input
        echo "Usage:"
        echo "  $0 <github-personal-access-token>"
        ...
        exit 1
      else
        # Read from stdin
        GITHUB_TOKEN=$(cat | tr -d '\n\r')
      fi
    else
      # Read from argument
      GITHUB_TOKEN=$1
    fi

    SECRET_NAME="github-oauth-token"

    echo "Creating secret in AWS Secrets Manager..."

    # Check if secret already exists
    if aws secretsmanager describe-secret --secret-id "$SECRET_NAME" >/dev/null 2>&1; then
      echo "Secret already exists. Updating..."
      aws secretsmanager update-secret \
        --secret-id "$SECRET_NAME" \
        --secret-string "$GITHUB_TOKEN"
    else
      echo "Creating new secret..."
      aws secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --description "GitHub OAuth token for CI/CD pipeline" \
        --secret-string "$GITHUB_TOKEN"
    fi

    echo "✅ GitHub token stored successfully!"
    echo "You can now run: cdk deploy --all"

This script takes a parameter which could be either an argument or a piped input. The GIT OAuth token should already be
acquired from GitHub. In order to do that, proceed as follows:

  1. Go to: https://github.com/settings/tokens". 
  2. Click `Generate new token (classic)`
  3. Select `repo scope`
  4. Copy the generated token.

The othe configuration file, `src/main/resources/application.properties`, contains the following key properties:

    # CI/CD Configuration
    cdk.cicd.repository.name=${CONTAINER_IMAGE_GROUP}/${CONTAINER_IMAGE_NAME}
    cdk.cicd.github.owner=${CONTAINER_IMAGE_GROUP}
    cdk.cicd.github.repo=aws-cdk-quarkus
    cdk.cicd.github.token-secret=github-oauth-token

    # EKS Configuration
    cdk.infrastructure.eks.namespace=customer-service
    cdk.infrastructure.eks.cluster-name=customer-service-cluster
    cdk.infrastructure.eks.service-account-name=customer-service-account
    ...

In addition to these configuration files and scripts, the class `CiCdConfig` uses the MP Config API to define properties 
relative to different services and stages of the CI/CD pipeline.

    @ConfigMapping(prefix = "cdk.cicd")
    public interface CiCdConfig
    {
      RepositoryConfig repository();
      GitHubConfig github();
      BuildConfig build();
      PipelineConfig pipeline();
      ...
    }

As we can see, `CiCdConfig` is an interface which contains several sub-interfaces, one for each service or stage. Each 
sub-interface defines a set of properties that are used to configure the corresponding service or stage, for exzmple:

    ...
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
    ...

## The CDK Stacks

The IaC code is organized into several CDK stacks, each responsible for a specific aspect of the infrastructure.

### The `VpcStack`

This stack creates the foundational networking infrastructure for the entire solution. It provisions a VPC (*Virtual Private
Cloud*) with multi-AZ (*Availability Zone*) support for high availability. The VPC is configured with both public and 
private subnets across multiple availability zones, as specified by the `maxAzs` configuration property (default: 2). The
stack also creates NAT Gateways to enable outbound internet access for resources in private subnets, with the number 
controlled by the `natGateways` property (default: 1). This VPC serves as the network foundation for all other stacks, 
including the EKS cluster, RDS database, and ElastiCache Redis instances. The implementation is minimal, as shown below:

    vpc = Vpc.Builder.create(this, "EksVpc")
      .maxAzs(config.vpc().maxAzs())           // Default: 2 AZs
      .natGateways(config.vpc().natGateways()) // Default: 1 NAT Gateway
      .build();

The code above uses the `software.amazon.awscdk.services.ec2.Vpc` CDK construct that automatically creates 6 subnets across
two AZs:

  - 2 public subnets (one per AZ) connected to an IGW (*Internet Gateway*). An IGW is a horizontally scaled, redundant AWS-managed component that allows bidirectional communication between resources in the VPC and the internet. It enables resources with public IP addresses to receive inbound traffic from the internet and send outbound traffic to the internet. In our case, it is used as an NLB (*Network Load Balancer*) which receives external traffic.

  - 2 private subnets with "egress" (one per AZ) connected to a NAT Gateway. A Nat Gateway is a managed service that enables resources in private subnets to initiate outbound connections to the internet (for software updates, API calls, etc.) while preventing unsolicited inbound connections from the internet. In this context, "egress" means outbound-only traffic flow. These 2 private subnets are used for EKS Fargate pods, RDS database and ElastiCache Redis which require all outbound internet access but should not be directly accessible from the internet.

  - 2 isolated subnets (one per AZ). These subnets have neither IGW, nor NatGateway and, hence, they don't have internet connectivity. They are created by default by the `Vpc` construct but they aren't used in this project as they are typically dedicated to highly sensitive resources that should never communicate with the internet.

The `maxAzs` property (default: 2) determines how many availability zones to span for high availability. The `natGateways`
property (default: 1) controls the number of NAT Gateways - using 1 instead of 2 reduces costs but creates a single point
of failure for outbound internet connectivity.

This VPC serves as the network foundation for all other stacks, including the EKS cluster, RDS database, and ElastiCache
Redis instances. We need to mention that any AWS account has a default VPC and that we could have used it here, instead
of creating another one. While this alternative would have been much simpler with no additional network cost, having a 
dedicated VPC is a more "production ready" solution, as it provides better isolation, customized CIDR blocks and more 
subnets.

### The `EksClusterStack`

This is the core infrastructure stack that creates and configures the EKS cluster with a Fargate compute profile. The stack
performs the following several critical operations:

  1. creates an EKS cluster (version 1.34) with API authentication mode and public endpoint access. The cluster is deployed in the private subnets of the VPC for enhanced security.

  2. adds to the previous created cluster a Fargate profile that targets the `customer-service` namespace, ensuring all pods in this namespace run on Fargate serverless compute. The profile's pod execution role is granted CloudWatch Logs permissions for centralized logging.

  3. sets up a Kubernetes ServiceAccount with IRSA (*IAM Roles for Service Accounts*), granting the pods secure access to AWS services without embedding credentials. The service account is granted permissions to connect to the RDS database and read secrets from AWS Secrets Manager.

  4. programmatically creates Kubernetes manifests including a namespace for workload isolation, a `ConfigMap` containing database and Redis connection strings, a deployment and a service resource, loaded from the YAML file in the `resources/k8s` directory.

     
    public void initStack() throws IOException
    {
      createCluster();
      KubernetesManifest namespace = createNamespace();
      addFargateProfile();
      ServiceAccount serviceAccount = setupServiceAccountWithIAM();
      serviceAccount.getNode().addDependency(namespace);
      KubernetesManifest configMap = addConfigMap();
      configMap.getNode().addDependency(serviceAccount);
      addDeploymentAndService(configMap);
    }

The stack establishes dependencies to ensure resources are created in the correct order, with the `ConfigMap` depending on
the `ServiceAccount`, and the `Deployment` depending on the `ConfigMap`.

    @SuppressWarnings("unchecked")
    private void addDeploymentAndService(KubernetesManifest configMap) throws IOException
    {
      List<Map<String, Object>> manifests = loadYamlManifests("k8s/customer-service.yaml");
      KubernetesManifest previous = configMap;
      for (int i = 0; i < manifests.size(); i++)
      {
        KubernetesManifest current =
          cluster.addManifest("CustomerService-%d".formatted(i), manifests.get(i));
        current.getNode().addDependency(previous);
        previous = current;
      }
    }

The code above shows how the file `customer-service.yaml`, containing the `ServiceAccount` and the `Deployment` manifests,
is parsed and the manifests added to the cluster, each one being dependent of the previous one, in order to prevent possible
cyclic dependencies.

### The `CiCdPipelineStack`

This stack implements a complete CI/CD pipeline using AWS native services to automate the build and deployment process. 
It consists of three stages:

  1. Source Stage: integrates with GitHub using a webhook trigger. When code is pushed to the repository, the pipeline automatically retrieves the source code using a GitHub OAuth token stored in AWS Secrets Manager.

  2. Build Stage: Uses AWS CodeBuild with a Standard 7.0 Linux image to build the Quarkus application, create a Docker image using the JIB Maven plugin and push the image to Amazon ECR (*Elastic Container Registry*). The build project has privileged mode enabled for Docker operations and is granted necessary IAM permissions for ECR operations.

  3. Deploy Stage: Uses a separate CodeBuild project to update the kubeconfig to access the EKS cluster and apply the updated Kubernetes manifests with the new container image. The deploy project is granted EKS cluster access through IAM role assumption.

The pipeline uses build specifications defined in `buildspecs/build-spec.yaml` and `buildspecs/deploy-spec.yaml`, and 
stores artifacts in S3 between stages. All configuration is externalized through the `CiCdConfig` interface using 
MicroProfile Config.

      public void initStack()
      {
        IRepository ecrRepo = Repository.fromRepositoryName(this, 
          "CustomerServiceRepo", cicdConfig.repository().name());

        Project buildProject = Project.Builder.create(this, "CustomerServiceBuild")
          .source(Source.gitHub(GitHubSourceProps.builder()
          ...
          .build();
        ecrRepo.grantPullPush(buildProject);
        buildProject.addToRolePolicy(PolicyStatement.Builder.create()
          .actions(List.of("ecr:GetAuthorizationToken"))
          .resources(List.of("*"))
          .build());
        buildProject.addToRolePolicy(PolicyStatement.Builder.create()
          .actions(List.of("secretsmanager:GetSecretValue"))
          .resources(List.of("arn:aws:secretsmanager:eu-west-3:" + this.getAccount() + ":secret:redhat-registry-credentials-*"))
          .build());

        Project deployProject = Project.Builder.create(this, "CustomerServiceDeploy")
          ....
          build();
        deployProject.getRole().addManagedPolicy(
          ManagedPolicy.fromAwsManagedPolicyName("AmazonEKSClusterPolicy"));
        eksStack.getCluster().getRole().grantAssumeRole(deployProject.getRole());
        deployProject.addToRolePolicy(PolicyStatement.Builder.create()
          .actions(List.of("eks:DescribeCluster"))
          .resources(List.of(eksStack.getCluster().getClusterArn()))
          .build());

        GitHubSourceAction sourceAction = GitHubSourceAction.Builder.create()
          .actionName(cicdConfig.pipeline().actions().source())
          ...
         .build();

        CodeBuildAction buildAction = CodeBuildAction.Builder.create()
          .actionName(cicdConfig.pipeline().actions().build())
          ...
          .build();

        CodeBuildAction deployAction = CodeBuildAction.Builder.create()
          .actionName(cicdConfig.pipeline().actions().deploy())
          ...
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

The code above create two CodeBuild projects: a build and a deploy one. It assigns to them the required security policies,
like `AmazonEKSClusterPolicy` and it creates then three actions: one `GitHubSourceAction` and two `CodeBuildAction`, one
for the build and the other one for the deploy operation. Last but not least, a `Pipeline` is created and the three mentioned
actions are added as its stages.

### The `MonitoringStack`

This stack provides observability and monitoring capabilities for the EKS cluster and running applications. It creates 
a dedicated CloudWatch log group named `/aws/eks/customer-service` with a one-week retention policy to collect and store
logs from the EKS pods and cluster components and a CloudWatch dashboard named `customer-service-eks` that visualizes key
metrics including but not limited to pod CPU utilization from the EKS namespace.

    public void initStack()
    {
      LogGroup.Builder.create(this, "EksLogGroup")
        .logGroupName("/aws/eks/customer-service")
        .retention(RetentionDays.ONE_WEEK)
        .build();
      Dashboard dashboard = Dashboard.Builder.create(this, "CustomerServiceDashboard")
      .dashboardName("customer-service-eks")
      .build();
      dashboard.addWidgets(
        GraphWidget.Builder.create()
          .title("Pod CPU Utilization")
          .left(List.of(Metric.Builder.create()
            .namespace("AWS/EKS")
            .metricName("pod_cpu_utilization")
            .build()))
          .build()
      );
    }

This stack depends on the EksClusterStack to ensure the cluster exists before monitoring resources are created. The 
monitoring infrastructure enables real-time visibility into cluster health, performance metrics, and troubleshooting 
capabilities through centralized log aggregation.

## Building, deploying and testing

The API to be built a deployed on EKS with Fargate is the same as the one we used previously for the ECS project (see the
`customer-service-api` module). Other shared artifacts are provided by the `customer-service-cdk-common` module. Here is
their list:

  - `DatabaseConstruct`: implements a CDK construct for RDS (*Relational Database Service*) with PostgreSQL;
  - `RedisCluster`: implements a CDK construct for ElasticCache with Redis;
  - `RedisClusterProps`: gropus together, in one record, several common Redis properties like the cluster ID, the number of nodes, their types, etc.
  - `DatabaseStack`: implements a database CDK stack which includes the previous mentioned PostgreSQL and Redis constructs.

Since these common artifacts are all required in order to build and deploy our stack, they need to be installed in the 
local Maven repository:

    $ cd aws-cdk-quarkus/customer-service-api
    $ mvn clean install
    $ cd aws-cdk-quarkus/customer-service-cdk-common
    $ mvn clean install

The Maven build process of the `customer-service-api` will run 2 integration tests, one using RESTassured against the 
Quarkus embedded web service, the other against a full local containerized infrastructure described by a `docker-compose.yaml`
file. This has been fully documented and explained in te 1st part of this series.

Now, we can build, deploy and test our new stack. The `customer-service-eks` module provides two ways to do it:

  - in development mode, using minikube;
  - in production mode, using AWS infrastructure;

Please notice that `localstack`, which is a very practical way to test AWS based IaC code without the cloud heavyness and
costs, isn't an option here, as it doesn't support EKS, VPC, ECR, etc.

### Building, deploying and testing in dev mode

As mentioned, using the dev mode, all our stacks are deployed locally, on minikube. So, this mode requires minikube to be
installed and running.

The `pom.xml` file defines two profiles: 

  - a dev mode one named `local`;
  - a prod mode one named `e2e`;

Here is the dev mode one, which is also the default one:

    <profile>
      <id>local</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <build>
        <plugins>
          <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <executions>
              <execution>
                <id>start-minikube</id>
                <phase>pre-integration-test</phase>
                <goals><goal>exec</goal></goals>
                <configuration>
                  <executable>minikube</executable>
                  <arguments>
                    <argument>start</argument>
                    <argument>--driver=docker</argument>
                  </arguments>
                </configuration>
              </execution>
              <execution>
                <id>deploy-to-minikube</id>
                <phase>pre-integration-test</phase>
                <goals><goal>exec</goal></goals>
                <configuration>
                  <executable>bash</executable>
                  <arguments>
                    <argument>src/main/resources/scripts/deploy-to-minikube.sh</argument>
                  </arguments>
                </configuration>
              </execution>
              <execution>
                <id>stop-minikube</id>
                <phase>clean</phase>
                <goals><goal>exec</goal></goals>
                <configuration>
                  <executable>minikube</executable>
                  <arguments>
                    <argument>delete</argument>
                  </arguments>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>

As you can see, here we're using the `exec-maven-plugin` with 3 executions that starts minikube, deploy to minikube and, 
respectively, stop minikube. As already mentioned, minikube should be installed in order that the `local` profile be
effective and, the action with ID `start-minikube` simply executes the `start` command.

Once minikube started, the action with ID `deploy-to-minikube` executes the `deploy-to-minikube.sh` script, shown below:

    #!/bin/bash
    set -e

    echo ">>> Loading image..."
    docker save nicolasduminil/customers-api:1.0-SNAPSHOT | minikube image load -

    echo ">>> Creating namespace..."
    kubectl create namespace customer-service --dry-run=client -o yaml | kubectl apply -f -

    echo ">>> Deploying PostgreSQL and Redis..."
    kubectl apply -f src/test/resources/k8s/postgres-redis.yaml

    echo ">>> Waiting for database..."
    for i in {1..5}; do
      if kubectl get pod -l app=postgres -n customer-service 2>/dev/null | grep -q postgres; then
        break
      fi
      echo "Waiting for postgres pod to be created... ($i/5)"
      sleep 5
    done

    kubectl wait --for=condition=ready pod -l app=postgres -n customer-service --timeout=60s

    echo ">>> Deploying application..."
    kubectl apply -f target/kubernetes/minikube.yml

    echo ">>> Waiting for application..."
    for i in {1..5}; do
      if kubectl get pod -l app.kubernetes.io/name=customer-service-api -n customer-service 2>/dev/null | grep -q customer-service; then
        break
      fi
      echo "Waiting for app pod to be created... ($i/5)"
      sleep 5
    done
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=customer-service-api -n customer-service --timeout=120s

    echo ">>> Final status:"
    kubectl get all -n customer-service

    echo ">>> Starting port-forward..."
    kubectl port-forward -n customer-service service/customer-service-api 9090:80 > /dev/null 2>&1 &
    echo "Port-forward started (PID: $!)"
    sleep 2

The `deploy-to-minikube.sh` script above is structured for performing several operations. First, the Docker image 
`nicolasduminil/customers-api:1.0-SNAPSHOT` built during the previous step (the shared components) is loaded to minikube 
via the command `image load`. Then, the `kubectl` tool, which is another prerequisite, is used to create the customized 
namespace `customer-service` and to apply the two manifests: `postgresql-manifest.yaml` and `minikube.yml`. Last but not
least, after having waited that all the services be on, the same `kubectl` is used to start the port-forward process.

At that point we're able to test our API locally deployed on minikube using the Swagger UI. Fire your preferred browser
at http://localhost:9090/q/swagger-ui to take advantage of the 80 to 9090 port-forward. You're ready to test the API.

Please notice that the `minikube.yml` manifest file mentioned above is automatically generated by the JIB extension for
Quarkus, while the `postgres-redis.yaml` was written on the purpose, to define the Kubernetes deployment and service 
controllers associated to the PostgreSQL databse and Redis cache. Don't hesitate to have a look at this file and make sure
you understand what everything is about there.

### Building, deploying and testing in prod mode

While the Maven building process is the same and consists in running

    $ mvn -Pe2e -DskipTests clean install

deploying is, this time, a much longer and heavier operation as it targets real AWS infrastructure. Look at the `e2e`
Maven profile below:

    <profile>
      <id>e2e</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <executions>
              <execution>
                <id>deploy-to-aws</id>
                <phase>pre-integration-test</phase>
                <goals>
                  <goal>exec</goal>
                </goals>
                <configuration>
                  <executable>bash</executable>
                  <arguments>
                    <argument>./src/main/resources/scripts/deploy-to-aws.sh</argument>
                  </arguments>
                  <workingDirectory>${project.basedir}</workingDirectory>
                  <environmentVariables>
                    <CDK_DEFAULT_ACCOUNT>${CDK_DEFAULT_ACCOUNT}</CDK_DEFAULT_ACCOUNT>
                    <CDK_DEFAULT_REGION>${CDK_DEFAULT_REGION}</CDK_DEFAULT_REGION>
                    <CDK_DEFAULT_USER>${CDK_DEFAULT_USER}</CDK_DEFAULT_USER>
                    <CONTAINER_IMAGE_GROUP>${CONTAINER_IMAGE_GROUP}</CONTAINER_IMAGE_GROUP>
                    <CONTAINER_IMAGE_NAME>${CONTAINER_IMAGE_NAME}</CONTAINER_IMAGE_NAME>
                    <CONTAINER_PORT>${CONTAINER_PORT}</CONTAINER_PORT>
                  </environmentVariables>
                </configuration>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>

What this profile is doing is simply running the `deploy-to-aws.sh` script via the `exec-maven-plugin`.

    #!/bin/bash
    set -e
    ...
    ../customer-service-cdk-common/src/main/resources/scripts/deploy-ecr.sh

    echo ">>> Updating kubeconfig..."
    aws eks update-kubeconfig --region $CDK_DEFAULT_REGION --name customer-service-cluster

    echo ">>> Checking EKS access..."
    USER_ARN=$(aws sts get-caller-identity --query 'Arn' --output text)
    grant_eks_access "$USER_ARN" "current user"

    echo ">>> Granting EKS access to CodeBuild deploy role..."
    DEPLOY_ROLE_ARN=$(aws iam list-roles --query 'Roles[?contains(RoleName, `CustomerServiceDeployRole`)].Arn' --output text --region $CDK_DEFAULT_REGION)
    if [ -n "$DEPLOY_ROLE_ARN" ]; then
      grant_eks_access "$DEPLOY_ROLE_ARN" "deploy role"
    else
      echo ">>> Deploy role not found (pipeline not deployed yet)"
    fi

    echo ">>> Retrieving database password from Secrets Manager..."
    SECRET_ARN=$(jq -r '.DatabaseStack.DatabaseSecretArn' cdk-outputs.json)
    DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $SECRET_ARN --region $CDK_DEFAULT_REGION --query SecretString --output text | jq -r .password)

    echo ">>> Creating Kubernetes secret with database password..."
    kubectl create secret generic db-credentials \
      --from-literal=QUARKUS_DATASOURCE_PASSWORD="$DB_PASSWORD" \
      -n customer-service --dry-run=client -o yaml | kubectl apply -f -

    echo ">>> Waiting for pods to be ready..."
    kubectl wait --for=condition=ready pod -l app=customer-service-api -n customer-service --timeout=300s || true

    echo ">>> Deployment complete!"
    echo ">>> To access the API locally, run:"
    echo ">>>   ./src/main/resources/scripts/test-api.sh"
    echo ">>> Then test with:"
    echo ">>>   curl http://localhost:8080/q/health"

The script above contains several distinct sections. First, it runs the shared script `deploy-ecr.sh`, present in the 
`customer-service-cdk-common` module, which deploys to ECR (*Elastic Container Registry*) the image 
`nicoladuminil/customer-service-api::1.0-SNAPSHOT`, built previously, before running the `cdk deploy` command, which 
deploys to AWS all the CloudFormation stacks. This process is very complex and long and, depending on your network speed
, it may take 15 - 20 minutes.

Then the script updates the `.kube/config` file with the EKS cluster required parameters, such that it could be handled
further by `kubectl`. Next it grants the `AmazonEKSClusterAdminPolicy` to the current user and the deployer user, 
identified by the `CustomerServiceDeployRole`. Then it gets the AWS secret containing the PostgreSQL database user 
password and creates a Kubernetes secret to be used by the associated pod. Once that all the pods started and are healthy, 
the script displays instructions of how to proceed further for testing purposes.

Several tests are available, once that the deployment process has succeeded. First, an e2e test, named `CustomerServiceE2EIT`
can be run as folowws:

    $ mvn -Pe2e failsafe:integration-test

Here is the listing:

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

As you can see, the test extends the `AbstractCustomerServiceE2E` present in the shared module `customer-service-cdk-common`.
This abstract class defines the test case to be run as they are the same whatever the cloud runtime is, be it ECS or EKS.
The only operation specific to the cloud runtime is the port-forward process start, implemented by the method 
`startPortForward()`. 

Of course, you can test your API using the Swagger UI, as you did before, in dev mode. The only thing you need to do is 
to start the port-forward and, for this, the script `test-api.sh`, here below, comes very handy:

    #!/bin/bash
    echo ">>> Starting port-forward to access API locally..."
    echo ">>> API will be available at http://localhost:8080"

    nohup kubectl port-forward svc/customer-service-api-service -n customer-service 8080:80 2>/dev/null &

Then fire your preferred browser, as usual, at http://localhost:8080/q/swagger-ui. Other test scripts, like 
`load-distribution-demo.sh`, `perf-demo.sh`, `pods-monitoring.sh`, `scaling-demo.sh`, are available as well,
just run them.

Once you finished testing, please cancel the port-forwarding by running:

    $ pkill -f "kubectl port-forward"

And don't forget to clean up your cloud by running:

    $ cd aws-cdk-quarkus/customer-service/eks
    $ cdk destroy --all --force

A script named `destroy-all.sh` is also available for a more atomic destruction.


  1. `start`
1. Bootstrap CDK (First Time Only)

cdk bootstrap aws://${CDK_DEFAULT_ACCOUNT}/${CDK_DEFAULT_REGION}

2. Deploy Infrastructure


    # Build the project, deploy VPC, database, EKS cluster and CI/CD pipeline
    mvn -DskipTests -Pe2e clean install

    # Deploy VPC and Database
    cdk deploy DatabaseStack

    # Deploy EKS Cluster
    cdk deploy EksClusterStack

    # Deploy CI/CD Pipeline
    cdk deploy CiCdPipelineStack

3. Configure kubectl

    
    aws eks update-kubeconfig --name customer-service-cluster --region eu-west-3

4. Verify Deployment


    # Check cluster
    $ kubectl get nodes -n customer-service
    NAME                                                 STATUS   ROLES    AGE   VERSION
    fargate-ip-10-0-168-154.eu-west-3.compute.internal   Ready    <none>   98m   v1.34.2-eks-b3126f4
    fargate-ip-10-0-169-36.eu-west-3.compute.internal    Ready    <none>   33m   v1.34.2-eks-b3126f4
    fargate-ip-10-0-199-224.eu-west-3.compute.internal   Ready    <none>   32m   v1.34.2-eks-b3126f4
    fargate-ip-10-0-225-237.eu-west-3.compute.internal   Ready    <none>   98m   v1.34.2-eks-b3126f4

    # Check pods
    kubectl get pods -n customer-service
    NAME                                               READY   STATUS    RESTARTS   AGE
    customer-service-api-deployment-79c5c96558-dm56j   1/1     Running   0          33m
    customer-service-api-deployment-79c5c96558-nbpq5   1/1     Running   0          34m


    # Check services
    kubectl get services -n customer-service
    NAME                           TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)        AGE
    customer-service-api-service   LoadBalancer   172.20.148.185   <pending>     80:32401/TCP   99m


## CI/CD Pipeline

Setup GitHub Webhook
Get webhook URL:

    aws codepipeline list-webhooks --region eu-west-3
    {
      "webhooks": [
        {
          "definition": {
            "name": "CustomerServicePipelineSour-ZunuWCjwpESR",
            "targetPipeline": "CiCdPipelineStack-CustomerServicePipelineB3195C39-t9UMJeMAQlDN",
            "targetAction": "GitHub_Source",
            "filters": [
              {
                "jsonPath": "$.ref",
                "matchEquals": "refs/heads/{Branch}"
              }
            ],
            "authentication": "GITHUB_HMAC",
            "authenticationConfiguration": {
              "SecretToken": "****"
            }
          },
          "url": "https://eu-west-3.webhooks.aws/trigger?t=eyJlbmNyeXB0ZWREYXRhIjoiUVJzNnYweXJ2UXg2SjVTL3VaVWUvMzlpcGJES1RFZmFmc3lvbTJ3c2UyUnFoWGlwVEV4SldSVWRFOGU2cXF2M3lNUWRxN2F1eDRNMzY0eE9lZXJyU3FOVFZsVU1TbklTTG1EUjRXQkZJbFR1WW1zaTU2UzVuaVRmdjdKbnlPemxMdWx5V01zRFE4a2hnNURZMzhPN0JUTFk2ZFlpSXVTTStxTkVhbEdZYTlPV1JnPT0iLCJpdlBhcmFtZXRlclNwZWMiOiJ3bHFhaXQxMGF1UjJ3bStOIiwibWF0ZXJpYWxTZXRTZXJpYWwiOjF9&v=1",
          "lastTriggered": "2025-12-16T16:06:14.628000+01:00",
          "arn": "arn:aws:codepipeline:eu-west-3:...:webhook:CustomerServicePipelineSour-ZunuWCjwpESR",
          "tags": []
        }
      ]
    }



Monitor Pipeline
Check pipeline status

    $ aws codepipeline list-pipelines --region eu-west-3 --query 'pipelines[?starts_with(name, `CiCdPipelineStack`)].name' --output text
    CiCdPipelineStack-CustomerServicePipelineB3195C39-t9UMJeMAQlDN
    $ aws codepipeline get-pipeline --name $PIPELINE --region eu-west-3 --query 'pipeline.stages[?name==`Build`].actions[0].configuration.ProjectName' --output text
    CustomerServiceBuild0A9B7C3-YIk2RDA0JP1B
    aws logs tail /aws/codebuild/CustomerServiceBuild0A9B7C3-YIk2RDA0JP1B --since 30m --follow --region eu-west-3

Integration Tests
Run end-to-end tests against the deployed EKS cluster:

mvn failsafe:integration-test

Copy
bash
The tests automatically:

Wait for deployment to be ready

Establish port-forward to the service

Execute API tests

Clean up port-forward

Manual Testing with Port-Forward
Forward traffic to the service

    nohup kubectl port-forward svc/customer-service-api-service -n customer-service 8080:80 2>/dev/null &

Access Swagger UI
open http://localhost:8080/q/swagger-ui

API Endpoints

    GET /customers - List all customers
    POST /customers - Create a new customer
    GET /customers/{id} - Get customer by ID
    PUT /customers/{id} - Update customer
    DELETE /customers/{id} - Delete customer

Infrastructure Components
EKS Cluster
Version: 1.34

Compute: Fargate serverless

Authentication: API mode with IRSA (IAM Roles for Service Accounts)

Networking: Private subnets with NAT gateway

Database (RDS PostgreSQL)
Instance: db.t3.micro

Storage: 20GB

Backup: Automated daily backups

Secrets: Managed by AWS Secrets Manager

Cache (ElastiCache Redis)
Node Type: cache.t3.micro

Nodes: 1

Purpose: Customer data caching

Kubernetes Resources
Namespace: customer-service

Deployment: 2 replicas with rolling updates

Service: LoadBalancer type with AWS NLB

ServiceAccount: IRSA-enabled for AWS service access

ConfigMap: Database and Redis connection strings

Secret: Database credentials from Secrets Manager

Monitoring and Logs
CloudWatch Logs

View application logs
    
    $ kubectl logs -f deployment/customer-service-api-deployment -n customer-service
    Found 2 pods, using pod/customer-service-api-deployment-79c5c96558-nbpq5
    Listening for transport dt_socket at address: 5005
    __  ____  __  _____   ___  __ ____  ______ 
    --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
    -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
    --\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
    2025-12-16 15:09:54,074 INFO  [fr.sim.wor.cus.con.SecretManagerConfig] (main) ### AWS Secrets Manager not configured - using standard datasource configuration
    2025-12-16 15:09:54,193 INFO  [io.quarkus] (main) customer-service-api 1.0-SNAPSHOT on JVM (powered by Quarkus 3.29.0) started in 7.077s. Listening on: http://0.0.0.0:8080
    2025-12-16 15:09:54,194 INFO  [io.quarkus] (main) Profile prod activated.
    2025-12-16 15:09:54,194 INFO  [io.quarkus] (main) Installed features: [agroal, cdi, hibernate-orm, hibernate-orm-panache, hibernate-validator, jdbc-postgresql, narayana-jta, redis-client, rest, rest-client, rest-client-jsonb, rest-jsonb, smallrye-context-propagation, smallrye-health, smallrye-openapi, swagger-ui, vertx]

View all logs from pods

    $ kubectl logs -f -l app=customer-service-api -n customer-service

Deployment Status
Check rollout status

    $ kubectl rollout status deployment/customer-service-api-deployment -n customer-service
    deployment "customer-service-api-deployment" successfully rolled out

View deployment details

    $ kubectl describe deployment customer-service-api-deployment -n customer-service
    Name:                   customer-service-api-deployment
    Namespace:              customer-service
    CreationTimestamp:      Tue, 16 Dec 2025 15:04:40 +0100
    Labels:                 aws.cdk.eks/prune-c80beddad564c9e8cb9cc132edee8fbb335e829314=
    Annotations:            deployment.kubernetes.io/revision: 2
    Selector:               app=customer-service-api
    Replicas:               2 desired | 2 updated | 2 total | 2 available | 0 unavailable
    StrategyType:           RollingUpdate
    MinReadySeconds:        0
    RollingUpdateStrategy:  25% max unavailable, 25% max surge
    Pod Template:
      Labels:           app=customer-service-api
      Service Account:  customer-service-account
      Containers:
        customer-service-api-container:
         Image:      495913029085.dkr.ecr.eu-west-3.amazonaws.com/nicolasduminil/customers-api:84ae6c1d9ad6a589e5caaab2b1e39e6d28af7f43
         Port:       8080/TCP
         Host Port:  0/TCP
         Environment Variables from:
           customer-service-config  ConfigMap  Optional: false
           db-credentials           Secret     Optional: false
         Environment:               <none>
         Mounts:                    <none>
      Volumes:                     <none>
      Node-Selectors:              <none>
      Tolerations:                 <none>
    Conditions:
      Type           Status  Reason
      ----           ------  ------
      Available      True    MinimumReplicasAvailable
      Progressing    True    NewReplicaSetAvailable
    OldReplicaSets:  customer-service-api-deployment-7d547b8475 (0/0 replicas created)
    NewReplicaSet:   customer-service-api-deployment-79c5c96558 (2/2 replicas created)
    Events:
      Type    Reason             Age   From                   Message
      ----    ------             ----  ----                   -------
      Normal  ScalingReplicaSet  53m   deployment-controller  Scaled up replica set customer-service-api-deployment-79c5c96558 from 0 to 1
      Normal  ScalingReplicaSet  52m   deployment-controller  Scaled down replica set customer-service-api-deployment-7d547b8475 from 2 to 1
      Normal  ScalingReplicaSet  52m   deployment-controller  Scaled up replica set customer-service-api-deployment-79c5c96558 from 1 to 2
      Normal  ScalingReplicaSet  52m   deployment-controller  Scaled down replica set customer-service-api-deployment-7d547b8475 from 1 to 0

Troubleshooting
Pipeline Failures
Build fails with ECR authentication error:

Verify ECR repository exists
aws ecr describe-repositories --region eu-west-3

Check IAM permissions

    $ aws iam get-role-policy --role-name <build-role-name> --policy-name <policy-name>

Deploy fails with kubectl errors:

Verify EKS cluster access

    $ aws eks describe-cluster --name customer-service-cluster --region eu-west-3
    {
      "cluster": {
        "name": "customer-service-cluster",
        "arn": "arn:aws:eks:eu-west-3:495913029085:cluster/customer-service-cluster",
        "createdAt": "2025-12-16T14:50:54.502000+01:00",
        "version": "1.34",
        "endpoint": "https://DA327A9B67B2E7884D40DBFD3E8284D4.gr7.eu-west-3.eks.amazonaws.com",
        "roleArn": "arn:aws:iam::495913029085:role/EksClusterStack-CustomerServiceClusterRoleB6D74166-CsT5r0otWnyu",
        ...
      }
    }

Update kubeconfig

    $ aws eks update-kubeconfig --name customer-service-cluster --region eu-west-3

Application Issues
Pods not starting:

Check pod events

    $ kubectl describe pod <pod-name> -n customer-service
    Name:                 customer-service-api-deployment-79c5c96558-dm56j
    Namespace:            customer-service
    Priority:             2000001000
    Priority Class Name:  system-node-critical
    Service Account:      customer-service-account
    Node:                 fargate-ip-10-0-199-224.eu-west-3.compute.internal/10.0.199.224
    Start Time:           Tue, 16 Dec 2025 16:10:21 +0100
    Labels:               app=customer-service-api
    ....

Check logs

    $ kubectl logs <pod-name> -n customer-service

Database connection errors:

Verify RDS endpoint

    $ aws rds describe-db-instances --region eu-west-3
    {
      "DBInstances": [
        {
          "DBInstanceIdentifier": "databasestack-customerdatabase306e9ff5-urrtgx3v4qbi",
          "DBInstanceClass": "db.t3.micro",
          "Engine": "postgres",
          "DBInstanceStatus": "available",
          "MasterUsername": "postgres",
          "DBName": "customers",
          "Endpoint": {
            "Address": "databasestack-customerdatabase306e9ff5-urrtgx3v4qbi.c9gccseo2c8v.eu-west-3.rds.amazonaws.com",
            "Port": 5432,
            "HostedZoneId": "ZMESEXB7ZGGQ3"
          },
        ...
        }
      ...
      ]
    }


Key Features
✅ Serverless container execution with EKS Fargate

✅ Infrastructure as Code with AWS CDK in Java

✅ Automated CI/CD with GitHub integration

✅ IRSA for secure AWS service access without credentials

✅ Redis caching for improved performance

✅ Automated database secret rotation

✅ Rolling updates with zero downtime

✅ Comprehensive integration tests

✅ OpenAPI/Swagger documentation

Technologies
Framework: Quarkus 3.x

Language: Java 21

IaC: AWS CDK

Container Orchestration: Kubernetes on AWS EKS

Database: PostgreSQL (RDS)

Cache: Redis (ElastiCache)

CI/CD: AWS CodePipeline, CodeBuild

Testing: JUnit 5, REST Assured

