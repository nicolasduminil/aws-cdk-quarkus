#!/bin/bash
set -e

echo ">>> Loading image..."
docker save nicolasduminil/customers-api:1.0-SNAPSHOT | minikube image load -

echo ">>> Creating namespace..."
kubectl create namespace customer-service --dry-run=client -o yaml | kubectl apply -f -

echo ">>> Deploying PostgreSQL and Redis..."
kubectl apply -f src/test/resources/k8s/postgres-redis.yaml

echo ">>> Waiting for database..."
kubectl wait --for=condition=ready pod -l app=postgres -n customer-service --timeout=60s

echo ">>> Deploying application..."
kubectl apply -f target/kubernetes/minikube.yml

echo ">>> Waiting for application..."
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=customer-service-api -n customer-service --timeout=120s

echo ">>> Final status:"
kubectl get all -n customer-service
