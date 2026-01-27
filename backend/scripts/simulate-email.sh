echo "🚀 Simulating inbound job email..."
curl -X POST http://localhost:8080/api/webhooks/inbound-email \
-H "Content-Type: application/json" \
-d '{
  "headers": {
    "subject": "Application Received: Senior Java Developer",
    "from": "recruiter@google.com"
  },
  "plain": "Hi Hari, we received your application for the Java role. We will be in touch soon!"
}'
echo -e "\n✅ Check your local dashboard at http://localhost:4200/app/dashboard"