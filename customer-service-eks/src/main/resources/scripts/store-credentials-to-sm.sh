aws secretsmanager create-secret \
  --name redhat-registry-credentials \
  --secret-string '{"username":"your-redhat-username","password":"your-redhat-password"}' \
  --region eu-west-3
