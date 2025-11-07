#!/bin/bash
source ../.env
export AWS_ACCESS_KEY_ID=$(aws configure get aws_access_key_id)
export AWS_SECRET_ACCESS_KEY=$(aws configure get aws_secret_access_key)
echo ">>> Testing CDK infrastructure..."
mvn -Dquarkus.profile=cdk -DCDK_DEFAULT_ACCOUNT=$CDK_DEFAULT_ACCOUNT -DCDK_DEFAULT_REGION=$CDK_DEFAULT_REGION -DskipTests clean package
echo ">>> Synthesizing CDK templates..."
cdk synth --profile cdk --no-staging --quiet
read -p ">>> Deploy to AWS dev environment? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
  echo ">>> Deploying CDK templates $AWS_ACCESS_KEY_ID $AWS_SECRET_ACCESS_KEY"
    cdk deploy --all --require-approval never
fi
