#!/bin/bash
source .env
echo ">>> Testing CDK infrastructure..."
mvn clean package
echo ">>> Synthesizing CDK templates..."
cdk synth --profile cdk
read -p ">>> Deploy to AWS dev environment? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cdk deploy --profile cdk --require-approval never --profile cdk
fi
