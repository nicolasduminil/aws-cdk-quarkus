#!/bin/bash
set -e
echo "=== GitHub Token Setup for CI/CD Pipeline ==="

# Read token from stdin or argument
if [ $# -eq 0 ]; then
    if [ -t 0 ]; then
        # No arguments and no piped input
        echo "Usage:"
        echo "  $0 <github-personal-access-token>"
        echo "  cat token.txt | $0"
        echo "  echo 'ghp_token' | $0"
        echo ""
        echo "Steps to get GitHub token:"
        echo "1. Go to: https://github.com/settings/tokens"
        echo "2. Click 'Generate new token (classic)'"
        echo "3. Select 'repo' scope"
        echo "4. Copy the generated token"
        exit 1
    else
        # Read from stdin
        GITHUB_TOKEN=$(cat | tr -d '\n\r')
    fi
else
    # Read from argument
    GITHUB_TOKEN=$1
fi

SECRET_NAME="github-oauth-token"

echo "Creating secret in AWS Secrets Manager..."

# Check if secret already exists
if aws secretsmanager describe-secret --secret-id "$SECRET_NAME" >/dev/null 2>&1; then
    echo "Secret already exists. Updating..."
    aws secretsmanager update-secret \
        --secret-id "$SECRET_NAME" \
        --secret-string "$GITHUB_TOKEN"
else
    echo "Creating new secret..."
    aws secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --description "GitHub OAuth token for CI/CD pipeline" \
        --secret-string "$GITHUB_TOKEN"
fi

echo "✅ GitHub token stored successfully!"
echo "You can now run: cdk deploy --all"
