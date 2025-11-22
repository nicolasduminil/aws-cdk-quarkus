#!/bin/bash
set -e
STACK_NAME="EksClusterStack"
echo "=== EKS Deployment Fix Script ==="

# Step 1: Get cluster name
echo "1. Finding EKS cluster..."
CLUSTER_NAME=$(aws eks list-clusters --query 'clusters[0]' --output text 2>/dev/null || echo "")
if [ -n "$CLUSTER_NAME" ]; then
  echo "Found cluster: $CLUSTER_NAME"

  # Step 2: List and delete Fargate profiles
  echo "2. Checking Fargate profiles..."
  PROFILES=$(aws eks list-fargate-profiles --cluster-name "$CLUSTER_NAME" --query 'fargateProfileNames' --output text 2>/dev/null || echo "")
  if [ -n "$PROFILES" ] && [ "$PROFILES" != "None" ]; then
    for profile in $PROFILES; do
      echo "Deleting Fargate profile: $profile"
      aws eks delete-fargate-profile --cluster-name "$CLUSTER_NAME" --fargate-profile-name "$profile"
      echo "Waiting for profile deletion..."
      aws eks wait fargate-profile-deleted --cluster-name "$CLUSTER_NAME" --fargate-profile-name "$profile"
    done
  fi
  else
    echo "No EKS cluster found"
fi

# Step 3: Continue rollback or delete stack
echo "3. Checking stack status..."
STACK_STATUS=$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo "NOT_FOUND")
case $STACK_STATUS in
  "ROLLBACK_FAILED")
    echo "Continuing rollback..."
    aws cloudformation continue-update-rollback --stack-name "$STACK_NAME"
    aws cloudformation wait stack-rollback-complete --stack-name "$STACK_NAME"
    ;;
  "CREATE_FAILED"|"UPDATE_ROLLBACK_COMPLETE")
    echo "Deleting failed stack..."
    aws cloudformation delete-stack --stack-name "$STACK_NAME"
    aws cloudformation wait stack-delete-complete --stack-name "$STACK_NAME"
    ;;
  "NOT_FOUND")
    echo "Stack not found, ready to deploy"
    ;;
  *)
    echo "Stack status: $STACK_STATUS"
    ;;
esac

echo "=== Cleanup complete. Ready to redeploy ==="
echo "Run: cdk deploy --all"