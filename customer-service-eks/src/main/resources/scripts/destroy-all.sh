#!/bin/bash
set -e
echo ">>> Finding nested DatabaseStack..."
NESTED_STACK=$(aws cloudformation describe-stacks --region eu-west-3 --query 'Stacks[?contains(StackName,`DatabaseStack`)].StackName' --output text)
if [ ! -z "$NESTED_STACK" ]; then
    echo ">>> Found nested stack: $NESTED_STACK"
    STACK_STATUS=$(aws cloudformation describe-stacks --stack-name "$NESTED_STACK" --region eu-west-3 --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo "NOT_FOUND")

    if [ "$STACK_STATUS" != "NOT_FOUND" ] && [ "$STACK_STATUS" != "DELETE_COMPLETE" ]; then
        if [ "$STACK_STATUS" != "DELETE_IN_PROGRESS" ]; then
            echo ">>> Deleting nested stack: $NESTED_STACK (Status: $STACK_STATUS)"
            aws cloudformation delete-stack --stack-name "$NESTED_STACK" --region eu-west-3
        fi
        echo ">>> Waiting for nested stack deletion..."
        aws cloudformation wait stack-delete-complete --stack-name "$NESTED_STACK" --region eu-west-3 --cli-read-timeout 300 --cli-connect-timeout 60
    fi
else
    echo ">>> No nested DatabaseStack found"
fi
cho ">>> Destroying CDK stacks..."
cdk destroy CiCdPipelineStack --force
cdk destroy MonitoringStack --force
cdk destroy EksClusterStack --force
aws logs describe-log-groups --region eu-west-3 \
  --query 'logGroups[?contains(logGroupName,`customer-service`)||contains(logGroupName,`eks`)||contains(logGroupName,`codebuild`)].logGroupName' \
  --output text | xargs -I {} aws logs delete-log-group --log-group-name {} --region eu-west-3
