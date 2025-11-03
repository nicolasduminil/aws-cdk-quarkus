# 1. Local development
chmod +x scripts/*.sh
./scripts/local-dev.sh

# 2. Test CDK templates
./scripts/test-cdk.sh

# 3. Run integration tests
./scripts/integration-test.sh

# 4. Deploy to AWS (when ready)
cdk bootstrap  # First time only
cdk deploy --require-approval never