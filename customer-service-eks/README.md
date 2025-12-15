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
    cdk.cicd.repository.name=nicolasduminil/customers-api
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


    # Build the project
    mvn clean package

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
    kubectl get nodes

    # Check pods
    kubectl get pods -n customer-service

    # Check services
    kubectl get services -n customer-service

## CI/CD Pipeline

Setup GitHub Webhook
Get webhook URL:

aws codepipeline list-webhooks --region eu-west-3

Copy
Configure in GitHub:

Go to: https://github.com/<owner>/<repo>/settings/hooks

Add webhook with the URL from step 1

Content type: application/json

Secret: Your GitHub OAuth token

Events: Just the push event

Pipeline Stages
Source: Pulls code from GitHub on push

Build: Builds Docker image and pushes to ECR

Deploy: Updates Kubernetes deployment with new image

Monitor Pipeline
# Check pipeline status
aws codepipeline get-pipeline-state \
--name CiCdPipelineStack-CustomerServicePipelineB3195C39-NKgKkSzsXnXI \
--region eu-west-3

# View build logs
aws logs tail /aws/codebuild/<build-project-name> --follow --region eu-west-3

Copy
bash
Testing
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
# Forward traffic to the service
kubectl port-forward deployment/customer-service-api-deployment 8080:8080 -n customer-service

# Access Swagger UI
open http://localhost:8080/q/swagger-ui

Copy
bash
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
# View application logs
kubectl logs -f deployment/customer-service-api-deployment -n customer-service

# View all logs from pods
kubectl logs -f -l app=customer-service-api -n customer-service

Copy
bash
Deployment Status
# Check rollout status
kubectl rollout status deployment/customer-service-api-deployment -n customer-service

# View deployment details
kubectl describe deployment customer-service-api-deployment -n customer-service

# Check pod details
kubectl get pods -n customer-service -o wide

Copy
bash
Troubleshooting
Pipeline Failures
Build fails with ECR authentication error:

# Verify ECR repository exists
aws ecr describe-repositories --region eu-west-3

# Check IAM permissions
aws iam get-role-policy --role-name <build-role-name> --policy-name <policy-name>

Copy
bash
Deploy fails with kubectl errors:

# Verify EKS cluster access
aws eks describe-cluster --name customer-service-cluster --region eu-west-3

# Update kubeconfig
aws eks update-kubeconfig --name customer-service-cluster --region eu-west-3

Copy
bash
Application Issues
Pods not starting:

# Check pod events
kubectl describe pod <pod-name> -n customer-service

# Check logs
kubectl logs <pod-name> -n customer-service

Copy
bash
Database connection errors:

# Verify RDS endpoint
aws rds describe-db-instances --region eu-west-3

# Check security groups
kubectl get pods -n customer-service -o wide

Copy
bash
Cleanup
To avoid AWS charges, destroy all resources:

# Delete pipeline
cdk destroy CiCdPipelineStack

# Delete EKS cluster
cdk destroy EksClusterStack

# Delete database
cdk destroy DatabaseStack

# Delete VPC
cdk destroy VpcStack

Copy
bash
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

License
[Your License Here]

Author
Nicolas Duminil


This README provides comprehensive documentation covering architecture, setup, deployment, testing, troubleshooting, and cleanup. Would you like me to adjust any section or add more details?

Copy


@Pin Context
Active file

Rules

Claude Sonnet 4.5
Claude Sonnet 4.5

Amazon Q is loading...
