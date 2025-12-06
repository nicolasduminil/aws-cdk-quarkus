#!/bin/bash
set -e

../customer-service-cdk-common/src/main/resources/scripts/deploy-ecr.sh

echo ">>> Adding current IAM user to EKS cluster..."
CALLER_ARN=$(aws sts get-caller-identity --query Arn --output text)
echo ">>> Current identity: $CALLER_ARN"
kubectl --kubeconfig /dev/null patch configmap/aws-auth -n kube-system --patch "
data:
  mapUsers: |
    - userarn: $CALLER_ARN
      username: admin
      groups:
        - system:masters
" 2>/dev/null || \
aws eks update-kubeconfig --region $CDK_DEFAULT_REGION --name customer-service-cluster && \
kubectl patch configmap/aws-auth -n kube-system --patch "
data:
  mapUsers: |
    - userarn: $CALLER_ARN
      username: admin
      groups:
        - system:masters
"

echo ">>> Updating kubeconfig..."
aws eks update-kubeconfig --region $CDK_DEFAULT_REGION --name customer-service-cluster

echo ">>> Applying Kubernetes manifests..."
kubectl apply -f target/classes/k8s/customer-service.yaml --validate=false

echo ">>> Retrieving database password from Secrets Manager..."
SECRET_ARN=$(jq -r '.DatabaseStack.DatabaseSecretArn' cdk-outputs.json)
DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id $SECRET_ARN --region $CDK_DEFAULT_REGION --query SecretString --output text | jq -r .password)

echo ">>> Creating Kubernetes secret with database password..."
kubectl create secret generic db-credentials \
  --from-literal=username=postgres \
  --from-literal=password="$DB_PASSWORD" \
  -n customer-service --dry-run=client -o yaml | kubectl apply -f - --validate=false

echo ">>> Deployment complete!"
