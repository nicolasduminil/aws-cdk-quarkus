#!/bin/bash
echo ">>> Starting port-forward to access API locally..."
echo ">>> API will be available at http://localhost:8080"

nohup kubectl port-forward svc/customer-service-api-service -n customer-service 8080:80 2>/dev/null &
