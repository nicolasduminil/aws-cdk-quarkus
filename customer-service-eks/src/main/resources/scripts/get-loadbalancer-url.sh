echo ">>> Getting LoadBalancer URL..."
LB_URL=$(kubectl get svc customer-service-api-service -n customer-service -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo ">>> API URL: http://$LB_URL"
