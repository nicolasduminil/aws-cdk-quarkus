curl -X POST http://localhost:8080/customers \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john.doe@example.com"}' && echo
curl http://localhost:8080/customers && echo
