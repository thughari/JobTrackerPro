$UserEmail = "thughari3@gmail.com"
$Url = "http://127.0.0.1:8080/api/webhooks/inbound-email"

Write-Host "Simulating inbound job email for $UserEmail..." -ForegroundColor Cyan

$Body = @{
    headers = @{
        subject = "Interview Invitation - Google"
        from = "interview@Google.com"
        to = $UserEmail
    }
    plain = "Hi Hari, we reviewed your application for the Senior Software Engineer role at Google and want to schedule an interview!"
}

try {
    $Response = Invoke-RestMethod -Uri $Url -Method Post -Body ($Body | ConvertTo-Json) -ContentType "application/json"
    Write-Host "Server Response: $Response" -ForegroundColor Green
} catch {
    Write-Host " Failed to connect to server at $Url" -ForegroundColor Red
    Write-Host "Make sure your Spring Boot app is running on port 8080."
    Write-Error $_
}

Write-Host "Check your local dashboard at http://localhost:4200/app/dashboard"