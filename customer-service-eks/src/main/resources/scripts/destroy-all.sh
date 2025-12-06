#!/bin/bash
set -e

REGION=${CDK_DEFAULT_REGION:-eu-west-3}

echo ">>> Deleting Kubernetes resources first..."
kubectl delete namespace customer-service --ignore-not-found=true 2>/dev/null || echo "### Namespace already deleted or not found"

echo ">>> Finding nested DatabaseStack..."
NESTED_STACK=$(aws cloudformation describe-stacks --region $REGION --query 'Stacks[?contains(StackName,`DatabaseStack`)].StackName' --output text)
if [ ! -z "$NESTED_STACK" ]; then
    echo ">>> Found nested stack: $NESTED_STACK"
    STACK_STATUS=$(aws cloudformation describe-stacks --stack-name "$NESTED_STACK" --region $REGION --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo "NOT_FOUND")

    if [ "$STACK_STATUS" != "NOT_FOUND" ] && [ "$STACK_STATUS" != "DELETE_COMPLETE" ]; then
        if [ "$STACK_STATUS" != "DELETE_IN_PROGRESS" ]; then
            echo ">>> Deleting nested stack: $NESTED_STACK (Status: $STACK_STATUS)"
            aws cloudformation delete-stack --stack-name "$NESTED_STACK" --region $REGION
        fi
        echo ">>> Waiting for nested stack deletion..."
        aws cloudformation wait stack-delete-complete --stack-name "$NESTED_STACK" --region $REGION --cli-read-timeout 300 --cli-connect-timeout 60
    fi
else
    echo ">>> No nested DatabaseStack found"
fi

echo ">>> Destroying CDK stacks (excluding CDKToolkit)..."
cdk destroy EksClusterStack --force 2>/dev/null || echo "### EksClusterStack already deleted"
cdk destroy DatabaseStack --force 2>/dev/null || echo "### DatabaseStack already deleted"
cdk destroy VpcStack --force 2>/dev/null || echo "### VpcStack already deleted"
cdk destroy MonitoringStack --force 2>/dev/null || echo "### MonitoringStack already deleted"
cdk destroy CiCdPipelineStack --force 2>/dev/null || echo "### CiCdPipelineStack already deleted"

echo ">>> Cleaning up log groups..."
LOG_GROUPS=$(aws logs describe-log-groups --region $REGION \
  --query 'logGroups[?contains(logGroupName,`customer-service`)||contains(logGroupName,`EksClusterStack`)||contains(logGroupName,`codebuild`)].logGroupName' \
  --output text 2>/dev/null || echo "")

if [ ! -z "$LOG_GROUPS" ]; then
  for LOG_GROUP in $LOG_GROUPS; do
    echo ">>> Deleting log group: $LOG_GROUP"
    aws logs delete-log-group --log-group-name "$LOG_GROUP" --region $REGION 2>/dev/null || echo "### Log group already deleted: $LOG_GROUP"
  done
else
  echo ">>> No log groups to delete"
fi

echo ">>> Cleanup complete!"
echo ">>> Note: CDKToolkit stack preserved for future deployments"
