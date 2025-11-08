#!/bin/bash
registry=$CDK_DEFAULT_ACCOUNT.dkr.ecr.$CDK_DEFAULT_REGION.amazonaws.com
echo ">>> Creating ECR registry $registry"
aws ecr create-repository --repository-name $CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME  --region $CDK_DEFAULT_REGION 2>/dev/null || echo "### Repository already exists"
echo ">>> Logging into ECR..."
aws ecr get-login-password --region $CDK_DEFAULT_REGION | docker login --username AWS --password-stdin $registry
echo ">>> Tagging and pushing existing image..."
docker tag $CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME:1.0-SNAPSHOT $registry/$CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME:latest
docker push $registry/$CONTAINER_IMAGE_GROUP/$CONTAINER_IMAGE_NAME:latest
echo ">>> Checking if stack exists..."
if aws cloudformation describe-stacks --stack-name QuarkusCustomerManagementStack --region $CDK_DEFAULT_REGION >/dev/null 2>&1; then
  echo ">>> Stack exists - updating ECS service ..."
  CLUSTER_NAME=$(aws cloudformation describe-stack-resources --stack-name QuarkusCustomerManagementStack \
    --query 'StackResources[?ResourceType==`AWS::ECS::Cluster`].PhysicalResourceId' \
    --output text --region $CDK_DEFAULT_REGION)
  if [ -n "$CLUSTER_NAME" ]; then
    echo ">>> Found cluster: $CLUSTER_NAME - updating ECS service..."
    aws ecs update-service \
      --cluster $CLUSTER_NAME \
      --service customer-service \
      --force-new-deployment \
      --region $CDK_DEFAULT_REGION
    echo ">>> Waiting for service update to complete..."
    aws ecs wait services-stable \
      --cluster $CLUSTER_NAME \
      --services customer-service \
      --region $CDK_DEFAULT_REGION
    echo ">>> Service update complete!"
    exit 0
  fi
fi
echo ">>> Deploying full infrastructure..."
cdk deploy --all --require-approval never
echo ">>> Deployment finished !"
