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

