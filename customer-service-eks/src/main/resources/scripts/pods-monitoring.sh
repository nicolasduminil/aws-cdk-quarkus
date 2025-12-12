echo "Pod Resource Monitoring (without metrics-server)"
echo "==========================================="

echo "Pod Status:"
kubectl get pods -n customer-service -o wide

echo ""
echo "Pod Resource Requests/Limits:"
kubectl describe pods -n customer-service | grep -A 10 -E "(Name:|Containers:|Requests:|Limits:)" | grep -E "(Name:|cpu:|memory:)"

echo ""
echo "Pod Events (recent):"
kubectl get events -n customer-service --sort-by='.lastTimestamp' | tail -5
