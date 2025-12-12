aws eks create-access-entry \
  --cluster-name customer-service-cluster \
  --principal-arn arn:aws:iam::495913029085:user/nicolas \
  --region eu-west-3

aws eks associate-access-policy \
  --cluster-name customer-service-cluster \
  --principal-arn arn:aws:iam::495913029085:user/nicolas \
  --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \
  --access-scope type=cluster \
  --region eu-west-3
