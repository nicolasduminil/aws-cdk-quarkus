#!/bin/bash
echo ">>> Running infrastructure integration tests..."

# Get load balancer URL
LB_URL=$(aws cloudformation describe-stacks \
    --stack-name QuarkusCustomerManagemntStack \
    --region eu-west-3 \
    --query 'Stacks[0].Outputs[?OutputKey==`CustomerServiceLoadBalancerDNS`].OutputValue' \
    --output text)

echo "Testing against: http://$LB_URL"

# Run integration tests
mvn test -Dtest=InfrastructureIntegrationTest -Dtest.base.url=http://$LB_URL
