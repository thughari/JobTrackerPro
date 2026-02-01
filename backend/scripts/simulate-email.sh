#!/bin/bash
USER_EMAIL="thughari3@gmail.com"

echo "🚀 Simulating inbound job email for $USER_EMAIL..."

curl -X POST http://127.0.0.1:8080/api/webhooks/inbound-email \
-H "Content-Type: application/json" \
-d "{
  \"headers\": {
    \"subject\": \"Interview Invitation - Google\",
    \"from\": \"$USER_EMAIL\",
    \"to\": \"save@jobtrackerpro.com\"
  },
  \"plain\": \"Hi Hari, we reviewed your application for the Senior Software Engineer role at Google and want to schedule an interview!\"
}"

echo -e "\n\n✅ Check http://localhost:4200/app/dashboard"