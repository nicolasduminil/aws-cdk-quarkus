#!/bin/bash
source ../.env
echo ">>> Destroying CDK stack..."
cdk destroy --force
echo ">>> Cleaning up ECR repositories..."
aws ecr delete-repository \
  --repository-name nicolasduminil/customers-api \
  --force \
  --region $CDK_DEFAULT_REGION 2>/dev/null || echo "ECR repository not found or already deleted"
echo ">>> Cleaning up CloudWatch log groups..."
aws logs delete-log-group \
  --log-group-name /ecs/customer-service \
  --region $CDK_DEFAULT_REGION 2>/dev/null || echo "Log group not found or already deleted"
aws logs delete-log-group \
  --log-group-name /aws/ecs/containerinsights/CustomerCluster/performance \
  --region $CDK_DEFAULT_REGION 2>/dev/null || echo "Container insights log group not found"
echo ">>> Cleanup complete!"