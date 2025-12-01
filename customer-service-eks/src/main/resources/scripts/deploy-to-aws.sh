#!/bin/bash
../customer-service-cdk-common/src/main/resources/scripts/deploy-ecr.sh
echo ">>> Retrieving database password from Secrets Manager..."
SECRET_ARN=$(jq -r '.EksClusterStack.DatabaseSecretArn' cdk-outputs.json)
DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $SECRET_ARN --region $CDK_DEFAULT_REGION --query SecretString --output text | jq -r .password)

echo ">>> Updating Kubernetes secret with database password..."
aws eks update-kubeconfig --region $CDK_DEFAULT_REGION --name customer-service-cluster
kubectl create secret generic db-credentials \
  --from-literal=username=postgres \
  --from-literal=password=$DB_PASSWORD \
  -n customer-service --dry-run=client -o yaml | kubectl apply -f -

echo ">>> Restarting deployment to pick up new secret..."
kubectl rollout restart deployment/customer-service-api-deployment -n customer-service

echo ">>> The deployment has been restarted."