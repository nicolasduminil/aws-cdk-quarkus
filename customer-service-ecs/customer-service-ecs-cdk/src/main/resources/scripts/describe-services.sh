#!/bin/bash
source ../../.env
stack_resources=$(aws cloudformation describe-stack-resources \
  --stack-name QuarkusCustomerManagementStack \
  --query 'StackResources[?ResourceType==`AWS::ECS::Cluster`].PhysicalResourceId' \
  --output text --region $CDK_DEFAULT_REGION)
aws ecs describe-services \
  --cluster $stack_resources \
  --services customer-service \
  --region $CDK_DEFAULT_REGION \
  --query 'services[0].{Status:status,RunningCount:runningCount,DesiredCount:desiredCount,Events:events[0:3]}'
