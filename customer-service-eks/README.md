# Customer Service API on AWS EKS with Quarkus and CDK

A cloud-native customer management system built with Quarkus, deployed on AWS EKS (Elastic Kubernetes Service) using AWS CDK for Infrastructure as Code, with automated CI/CD pipeline.

## Architecture Overview

This solution implements a complete production-ready architecture:

- **Presentation Layer**: Quarkus REST API with OpenAPI/Swagger documentation
- **Application Layer**: Business logic with Quarkus Panache for data access
- **Data Layer**: PostgreSQL (RDS) for persistence, Redis (ElastiCache) for caching
- **Container Orchestration**: AWS EKS with Fargate for serverless container execution
- **Infrastructure as Code**: AWS CDK implemented in Quarkus
- **CI/CD**: Automated pipeline with AWS CodePipeline, CodeBuild, and GitHub integration

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker
- AWS CLI configured with appropriate credentials
- kubectl
- AWS CDK CLI: `npm install -g aws-cdk`
- GitHub account with OAuth token stored in AWS Secrets Manager

## Project Structure

    customer-service-eks/
    ├── src/main/java/
    │ └── fr/simplex_software/workshop/customer_service_eks/
        │ └── config/
          │ └── CiCdConfig.java # Pipeline configuration
        │ ├── CiCdPipelineStack.java # CI/CD pipeline
        │ ├── CustomerManagementEksApp.java # EKS cluster infrastructure
        │ ├── CustomerManagementEksMain.java # EKS cluster infrastructure
        │ ├── CustomerManagementEksProducer.java # EKS cluster infrastructure
        │ ├── EksClusterStack.java # EKS cluster infrastructure
        │ ├── MonitoringStack.java # EKS cluster infrastructure
        │ ├── VpcStack.java # CI/CD pipeline
    ├── src/main/resources/
    │ ├── buildspecs/
    │ │ ├── build-spec.yaml # CodeBuild build specification
    │ │ └── deploy-spec.yaml # CodeBuild deploy specification
    │ ├── k8s/
    │ │ └── customer-service.yaml # Kubernetes manifests
    │ ├── scripts/
          ...
    │ └── application.properties # Configuration
    └── src/test/java/
        └── fr/simplex_software/workshop/customer_service_eks/tests/
            └── CustomerServiceE2EIT.java # End-to-end integration tests


## Configuration

### Environment Variables

Set these in your environment or `.env` file:

    export CDK_DEFAULT_ACCOUNT=<your-aws-account-id>
    export CDK_DEFAULT_REGION=eu-west-3
    export CONTAINER_IMAGE_GROUP=<your-ecr-namespace>
    export CONTAINER_IMAGE_NAME=customers-api

### Application Properties
Key configurations in src/main/resources/application.properties:

    # CI/CD Configuration
    cdk.cicd.repository.name=${CONTAINER_IMAGE_GROUP}/${CONTAINER_IMAGE_NAME}
    cdk.cicd.github.owner=${CONTAINER_IMAGE_GROUP}
    cdk.cicd.github.repo=aws-cdk-quarkus
    cdk.cicd.github.token-secret=github-oauth-token

    # EKS Configuration
    cdk.infrastructure.eks.namespace=customer-service
    cdk.infrastructure.eks.cluster-name=customer-service-cluster
    cdk.infrastructure.eks.service-account-name=customer-service-account

### Deployment

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

