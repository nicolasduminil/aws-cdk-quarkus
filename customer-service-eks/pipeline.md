1. Redeploy the pipeline stack:

cd /home/nicolas/aws-cdk-quarkus/customer-service-eks
cdk deploy CiCdPipelineStack

Copy
bash
2. After deployment, CDK will automatically:

Create a webhook in AWS CodePipeline

Output the webhook URL and secret

3. Configure GitHub webhook:

Get the webhook details:

aws codepipeline list-webhooks --region eu-west-3

Copy
bash
This will show you the webhook URL. Then:

Go to your GitHub repository

Settings → Webhooks → Add webhook

Payload URL: Copy from the command output above

Content type: application/json

Secret: Your GitHub OAuth token (same one in Secrets Manager)

Events: Select "Just the push event"

Click "Add webhook"

4. Test it:

cd /path/to/your/github/repo
echo "# Test pipeline" >> README.md
git add README.md
git commit -m "Trigger pipeline"
git push origin main

Copy
bash
5. Monitor pipeline:

aws codepipeline get-pipeline-state --name <pipeline-name> --region eu-west-3

Copy
bash
The pipeline should now trigger automatically on every push to your GitHub repository!


Now configure GitHub webhook:

Go to your GitHub repository: https://github.com/<owner>/<repo>/settings/hooks

Add webhook with these settings:

Payload URL:

https://eu-west-3.webhooks.aws/trigger?t=eyJlbmNyeXB0ZWREYXRhIjoibitNaG4zMUppVDZRWU9yZzFvK3ZjZUM0dGdnKzlNNU9yWUt3c0IrUGFvb3NCMmh2cFVmR1NuN3FnVTU3RDBaTEcwcjQ2cEc3b1ZzZnQwRndSL2VocVlEeWo1NkNWc0FPTC9nSkVTR01DZkZGWUtxMzlQWGpkNm5DQ2pqRkx2NGc0N29qRFlBa2s1WXVkLy9WbmtxVEI4d085T3dzdWZLN1d0MEVlNTNDLzBPalBBPT0iLCJpdlBhcmFtZXRlclNwZWMiOiJEVWg3TnQrTFVQWUliV01vIiwibWF0ZXJpYWxTZXRTZXJpYWwiOjF9&v=1

Copy
Content type: application/json

Secret: Get from Secrets Manager:

aws secretsmanager get-secret-value --secret-id <your-github-token-secret> --region eu-west-3 --query SecretString --output text

Copy
bash
SSL verification: Enable

Events: Just the push event

Active: ✓ Checked

Test the pipeline:

cd /path/to/your/github/repo
echo "# Trigger CI/CD" >> README.md
git add README.md
git commit -m "Test pipeline trigger"
git push origin main

Copy
bash
Monitor execution:

aws codepipeline get-pipeline-state --name CiCdPipelineStack-CustomerServicePipelineB3195C39-NKgKkSzsXnXI --region eu-west-3

# Watch pipeline status
watch -n 5 'aws codepipeline get-pipeline-state --name CiCdPipelineStack-CustomerServicePipelineB3195C39-NKgKkSzsXnXI --region eu-west-3 | grep -A 5 "\"status\""'

# Or view build logs directly
aws logs tail /aws/codebuild/CustomerServiceBuild0A9B7C3-0WzIYfhDZIeh --follow --region eu-west-3


What happens next:

Build stage completes → builds Docker image → pushes to ECR

Deploy stage starts → updates Kubernetes deployment with new image

EKS pulls new image and rolls out update

To verify deployment after pipeline completes:

# Check deployment rollout
kubectl rollout status deployment/customer-service-api-deployment -n customer-service

# Verify new pods
kubectl get pods -n customer-service

# Check image version
kubectl describe deployment customer-service-api-deployment -n customer-service | grep Image

What happens next:

Build stage completes → builds Docker image → pushes to ECR

Deploy stage starts → updates Kubernetes deployment with new image

EKS pulls new image and rolls out update

To verify deployment after pipeline completes:

# Check deployment rollout
kubectl rollout status deployment/customer-service-api-deployment -n customer-service

# Verify new pods
kubectl get pods -n customer-service

# Check image version
kubectl describe deployment customer-service-api-deployment -n customer-service | grep Image