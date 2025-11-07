#!/bin/bash
source ../../.env
stack_resources=$(aws cloudformation describe-stack-resources \
  --stack-name QuarkusCustomerManagementStack \
  --query 'StackResources[?ResourceType==`AWS::ECS::Cluster`].PhysicalResourceId' \
  --output text --region $CDK_DEFAULT_REGION)
aws ecs describe-tasks --cluster $stack_resources --tasks $1