#!/bin/bash

echo "Performance Demo - Testing concurrent request handling"
echo "Current customer count:"
INITIAL_COUNT=$(curl -s http://localhost:8080/customers | jq length 2>/dev/null || echo "0")
echo "Initial customers: $INITIAL_COUNT"

echo ""
echo "Sending 20 concurrent POST requests..."
START_TIME=$(date +%s)

# Create customers concurrently but suppress curl output
for i in {1..20}; do
  (curl -s -X POST http://localhost:8080/customers \
    -H "Content-Type: application/json" \
    -d "{\"firstName\":\"User$i\",\"lastName\":\"Test\",\"email\":\"user$i@test.com\"}" \
    > /dev/null 2>&1 && echo "✅ User$i created") &
done

# Wait for all background jobs to complete
wait

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "All requests completed in $DURATION seconds"

echo ""
echo "Final customer count:"
FINAL_COUNT=$(curl -s http://localhost:8080/customers | jq length 2>/dev/null || echo "unknown")
echo "Final customers: $FINAL_COUNT"
echo "New customers created: $((FINAL_COUNT - INITIAL_COUNT))"

echo ""
echo "Performance summary:"
echo "- Concurrent requests: 20"
echo "- Total time: ${DURATION}s"
echo "- Average time per request: $((DURATION * 1000 / 20))ms (approx)"
echo "- Success rate: $((FINAL_COUNT - INITIAL_COUNT))/20 requests"