#!/bin/bash
registry=$CDK_DEFAULT_ACCOUNT.dkr.ecr.$CDK_DEFAULT_REGION.amazonaws.com
echo ">>> Creating ECR registry $registry"
aws ecr create-repository --repository-name $CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME  --region $CDK_DEFAULT_REGION 2>/dev/null || echo "### Repository already exists"
echo ">>> Logging into ECR..."
aws ecr get-login-password --region $CDK_DEFAULT_REGION | docker login --username AWS --password-stdin $registry
echo ">>> Tagging and pushing existing image..."
docker tag $CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME:1.0-SNAPSHOT $registry/$CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME:latest
docker push $registry/$CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME:latest
echo ">>> Deploying full infrastructure..."
cdk deploy --all --require-approval never
echo ">>> Deployment finished !"
