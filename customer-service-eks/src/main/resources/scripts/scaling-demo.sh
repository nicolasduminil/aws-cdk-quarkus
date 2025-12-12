kubectl scale deployment customer-service --replicas=4 -n customer-service
kubectl get pods -n customer-service -w
kubectl scale deployment customer-service --replicas=1 -n customer-service
