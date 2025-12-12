echo "Testing load balancing across pods..."
for i in {1..10}; do
  echo "Request $i:"
  curl -s http://localhost:8080/q/info | jq -r '.host' 2>/dev/null || \
  curl -s http://localhost:8080/customers | head -1
  sleep 0.5
done