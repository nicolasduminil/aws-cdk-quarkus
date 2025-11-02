#!/bin/bash
echo "Testing CDK infrastructure..."

# Build the project
./mvnw clean compile

# Validate CDK templates
echo "Synthesizing CDK templates..."
cdk synth --profile cdk

# Optional: Deploy to dev environment
read -p "Deploy to AWS dev environment? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cdk deploy --profile cdk --require-approval never
fi
