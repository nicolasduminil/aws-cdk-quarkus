# Get VPC CIDR
VPC_CIDR=$(aws ec2 describe-vpcs --region eu-west-3 \
  --filters "Name=tag:Name,Values=*EksVpcStack*" \
  --query 'Vpcs[0].CidrBlock' --output text)
echo "VPC CIDR: $VPC_CIDR"

# Get database security group
DB_SG=$(aws rds describe-db-instances --region eu-west-3 \
  --query 'DBInstances[?DBName==`customers`].VpcSecurityGroups[0].VpcSecurityGroupId' \
  --output text)
echo "Database Security Group: $DB_SG"

# Check ingress rules on port 5432
aws ec2 describe-security-groups --region eu-west-3 \
  --group-ids $DB_SG \
  --query 'SecurityGroups[0].IpPermissions[?ToPort==`5432`]'
