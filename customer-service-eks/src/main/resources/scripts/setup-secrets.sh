#!/bin/bash
set -e
echo ">>> Setting up Kubernetes secrets from AWS Secrets Manager..."
SECRET_ARN=$(aws cloudformation describe-stacks --stack-name EksClusterStack --query 'Stacks[0].Outputs[?OutputKey==`DatabaseSecretArn`].OutputValue' --output text)
CREDS=$(aws secretsmanager get-secret-value --secret-id $SECRET_ARN --query 'SecretString' --output text)
USERNAME=$(echo $CREDS | jq -r .username)
PASSWORD=$(echo $CREDS | jq -r .password)
aws eks update-kubeconfig --region ${CDK_DEFAULT_REGION} --name customer-service-cluster
kubectl create secret generic db-credentials \
  --from-literal=username=$USERNAME \
  --from-literal=password=$PASSWORD \
  -n customer-service \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl rollout restart deployment/customer-service-api-deployment -n customer-service
echo ">>> Secrets configured successfully!"