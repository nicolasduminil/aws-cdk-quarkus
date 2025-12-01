#!/bin/bash
set -e

echo ">>> Loading image..."
docker save nicolasduminil/customers-api:1.0-SNAPSHOT | minikube image load -

echo ">>> Creating namespace..."
kubectl create namespace customer-service --dry-run=client -o yaml | kubectl apply -f -

echo ">>> Deploying PostgreSQL and Redis..."
kubectl apply -f src/test/resources/k8s/postgres-redis.yaml

echo ">>> Waiting for database..."
for i in {1..5}; do
  if kubectl get pod -l app=postgres -n customer-service 2>/dev/null | grep -q postgres; then
    break
  fi
  echo "Waiting for postgres pod to be created... ($i/5)"
  sleep 5
done

kubectl wait --for=condition=ready pod -l app=postgres -n customer-service --timeout=60s

echo ">>> Deploying application..."
kubectl apply -f target/kubernetes/minikube.yml

echo ">>> Waiting for application..."
for i in {1..5}; do
  if kubectl get pod -l app.kubernetes.io/name=customer-service-api -n customer-service 2>/dev/null | grep -q customer-service; then
    break
  fi
  echo "Waiting for app pod to be created... ($i/5)"
  sleep 5
done
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=customer-service-api -n customer-service --timeout=120s

echo ">>> Final status:"
kubectl get all -n customer-service

echo ">>> Starting port-forward..."
kubectl port-forward -n customer-service service/customer-service-api 9090:80 > /dev/null 2>&1 &
echo "Port-forward started (PID: $!)"
sleep 2