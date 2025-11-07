#!/bin/bash
aws cloudformation describe-stack-events \
  --stack-name QuarkusCustomerManagementStack \
  --query 'StackEvents[0:5].[Timestamp,ResourceStatus,ResourceType,LogicalResourceId]' \
  --output table
