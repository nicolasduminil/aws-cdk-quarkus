#!/bin/bash
set -e

../customer-service-cdk-common/src/main/resources/scripts/deploy-ecr.sh

echo ">>> Updating kubeconfig..."
aws eks update-kubeconfig --region $CDK_DEFAULT_REGION --name customer-service-cluster

echo ">>> Checking EKS access..."
USER_ARN=$(aws sts get-caller-identity --query 'Arn' --output text)

# Check if access entry exists
if ! aws eks describe-access-entry --cluster-name customer-service-cluster --principal-arn $USER_ARN --region $CDK_DEFAULT_REGION >/dev/null 2>&1; then
  echo ">>> Creating EKS access entry for current user..."
  aws eks create-access-entry \
    --cluster-name customer-service-cluster \
    --principal-arn $USER_ARN \
    --region $CDK_DEFAULT_REGION

  aws eks associate-access-policy \
    --cluster-name customer-service-cluster \
    --principal-arn $USER_ARN \
    --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \
    --access-scope type=cluster \
    --region $CDK_DEFAULT_REGION
else
  echo ">>> EKS access entry already exists"
fi

echo ">>> Granting EKS access to CodeBuild deploy role..."
DEPLOY_ROLE_ARN=$(aws iam list-roles --query 'Roles[?contains(RoleName, `CustomerServiceDeployRole`)].Arn' --output text --region $CDK_DEFAULT_REGION)

if [ -n "$DEPLOY_ROLE_ARN" ]; then
  if ! aws eks describe-access-entry --cluster-name customer-service-cluster --principal-arn $DEPLOY_ROLE_ARN --region $CDK_DEFAULT_REGION >/dev/null 2>&1; then
    echo ">>> Creating EKS access entry for deploy role..."
    aws eks create-access-entry \
      --cluster-name customer-service-cluster \
      --principal-arn $DEPLOY_ROLE_ARN \
      --region $CDK_DEFAULT_REGION

    aws eks associate-access-policy \
      --cluster-name customer-service-cluster \
      --principal-arn $DEPLOY_ROLE_ARN \
      --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \
      --access-scope type=cluster \
      --region $CDK_DEFAULT_REGION
  else
    echo ">>> Deploy role access entry already exists"
  fi
else
  echo ">>> Deploy role not found (pipeline not deployed yet)"
fi

echo ">>> Retrieving database password from Secrets Manager..."
SECRET_ARN=$(jq -r '.DatabaseStack.DatabaseSecretArn' cdk-outputs.json)
DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $SECRET_ARN --region $CDK_DEFAULT_REGION --query SecretString --output text | jq -r .password)

echo ">>> Creating Kubernetes secret with database password..."
kubectl create secret generic db-credentials \
  --from-literal=QUARKUS_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  -n customer-service --dry-run=client -o yaml | kubectl apply -f -

echo ">>> Waiting for pods to be ready..."
kubectl wait --for=condition=ready pod -l app=customer-service-api -n customer-service --timeout=300s || true

echo ">>> Deployment complete!"
echo ">>> To access the API locally, run:"
echo ">>>   ./src/main/resources/scripts/test-api.sh"
echo ">>> Then test with:"
echo ">>>   curl http://localhost:8080/q/health"
