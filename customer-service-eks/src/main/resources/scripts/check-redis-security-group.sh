# Check if Redis security group has the rule
REDIS_SG=$(aws elasticache describe-cache-clusters --region eu-west-3 \
  --cache-cluster-id customer-cache-0001 \
  --show-cache-node-info \
  --query 'CacheClusters[0].SecurityGroups[0].SecurityGroupId' --output text)

echo "Redis Security Group: $REDIS_SG"

aws ec2 describe-security-groups --region eu-west-3 \
  --group-ids $REDIS_SG \
  --query 'SecurityGroups[0].IpPermissions[?ToPort==`6379`]'
