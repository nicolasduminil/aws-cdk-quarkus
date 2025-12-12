echo "High Availability Demo - Testing service continuity during pod failure"
echo "Current pods:"
kubectl get pods -n customer-service

echo ""
echo "Testing service before pod deletion..."
for i in {1..3}; do
  curl -s http://localhost:8080/customers > /dev/null && echo "✅ Request $i: Service available" || echo "❌ Request $i: Service failed"
  sleep 1
done

echo ""
echo "Deleting one pod..."
kubectl delete $(kubectl get pods -n customer-service -o name | head -1) -n customer-service

echo "Restarting port-forward..."
pkill -f "kubectl port-forward" 2>/dev/null
sleep 2
nohup kubectl port-forward service/customer-service-api-service 8080:80 -n customer-service 2>/dev/null &
sleep 3

echo ""
echo "Testing service during pod recovery..."
for i in {1..5}; do
  curl -s http://localhost:8080/customers > /dev/null && echo "✅ Request $i: Service still available" || echo "❌ Request $i: Service failed"
  sleep 2
done

echo ""
echo "Final pod status:"
kubectl get pods -n customer-service