param(
    [string]$UserEmail = "",
    [string]$Url = "http://127.0.0.1:8080/api/webhooks/inbound-email"
)

function Write-Section {
    param([string]$Message)

    Write-Host ""
    Write-Host "┌──────────────────────────────────────────────────────────┐" -ForegroundColor DarkCyan
    Write-Host ("│ " + $Message.PadRight(56) + " │") -ForegroundColor Cyan
    Write-Host "└──────────────────────────────────────────────────────────┘" -ForegroundColor DarkCyan
}

function Write-EmailPreview {
    param(
        [string]$Subject,
        [string]$From,
        [string]$Snippet
    )

    Write-Host ("  Subject : " + $Subject) -ForegroundColor White
    Write-Host ("  From    : " + $From) -ForegroundColor Gray
    Write-Host ("  Snippet : " + $Snippet) -ForegroundColor DarkGray
}

if ([string]::IsNullOrWhiteSpace($UserEmail)) {
    Write-Host "Please provide your login email so jobs are linked to your account." -ForegroundColor Yellow
    Write-Host "Example: .\simulate-email.ps1 -UserEmail \"your-email@example.com\"" -ForegroundColor Yellow
    return
}

Write-Section "Simulating inbound job emails for $UserEmail"

$EmailSamples = @(
    @{
        headers = @{
            subject = "Interview Invitation - Google"
            from = "interview@google.com"
            to = $UserEmail
        }
        plain = "Hi there, we reviewed your application for the Senior Software Engineer role at Google and want to schedule an interview this week."
    },
    @{
        headers = @{
            subject = "Application Received - Microsoft"
            from = "careers@microsoft.com"
            to = $UserEmail
        }
        plain = "Thanks for applying to Microsoft's Full Stack Engineer position. Your profile is now under review by our hiring team."
    },
    @{
        headers = @{
            subject = "Coding Challenge Reminder - Amazon"
            from = "noreply@amazon.jobs"
            to = $UserEmail
        }
        plain = "Friendly reminder: please complete your online assessment for the SDE II role within 72 hours to stay in consideration."
    },
    @{
        headers = @{
            subject = "Offer Update - Stripe"
            from = "talent@stripe.com"
            to = $UserEmail
        }
        plain = "Great news! We'd like to move forward with an offer discussion for the Backend Engineer role. Please share your availability."
    },
    @{
        headers = @{
            subject = "Application Status Update - Netflix"
            from = "jobs@netflix.com"
            to = $UserEmail
        }
        plain = "Thank you for your interest in Netflix. We've moved ahead with other candidates for now, but we'd love to stay in touch for future openings."
    }
)

$DeliveredCount = 0
$ProcessedCount = 0
$SkippedCount = 0
$UnknownUserCount = 0
$FailureCount = 0

foreach ($Email in $EmailSamples) {
    Write-Host ""
    Write-Host "────────────────────────────────────────────────────────────" -ForegroundColor DarkGray
    Write-EmailPreview -Subject $Email.headers.subject -From $Email.headers.from -Snippet $Email.plain

    try {
        $Response = Invoke-RestMethod -Uri $Url -Method Post -Body ($Email | ConvertTo-Json -Depth 6) -ContentType "application/json"
        $DeliveredCount++

        if ($Response -eq "Processed") {
            $ProcessedCount++
            Write-Host "  Status  : Processed" -ForegroundColor Green
        } elseif ($Response -eq "Skipped") {
            $SkippedCount++
            Write-Host "  Status  : Skipped (email not recognized as job update)" -ForegroundColor Yellow
        } elseif ($Response -eq "User Unknown") {
            $UnknownUserCount++
            Write-Host "  Status  : User Unknown (email does not match a registered account)" -ForegroundColor Yellow
        } else {
            Write-Host "  Status  : Delivered" -ForegroundColor Green
        }

        Write-Host ("  Server  : " + $Response) -ForegroundColor DarkGreen
    } catch {
        $FailureCount++
        Write-Host "  Status  : Failed to deliver" -ForegroundColor Red
        Write-Host "  Hint    : Make sure your Spring Boot app is running on port 8080." -ForegroundColor Yellow
        Write-Host ("  Error   : " + $_.Exception.Message) -ForegroundColor DarkRed
    }
}

Write-Section "Simulation summary"
Write-Host ("  Total emails  : " + $EmailSamples.Count) -ForegroundColor White
Write-Host ("  Delivered     : " + $DeliveredCount) -ForegroundColor Green
Write-Host ("  Processed     : " + $ProcessedCount) -ForegroundColor Cyan
Write-Host ("  Skipped       : " + $SkippedCount) -ForegroundColor Yellow
Write-Host ("  User Unknown  : " + $UnknownUserCount) -ForegroundColor Yellow
Write-Host ("  Failed        : " + $FailureCount) -ForegroundColor Red
Write-Host ""

if ($UnknownUserCount -gt 0) {
    Write-Host "Tip: Run with the exact same email you used to sign in to JobTrackerPro." -ForegroundColor Yellow
    Write-Host "Example: .\simulate-email.ps1 -UserEmail \"your-login-email@example.com\"" -ForegroundColor Yellow
}

Write-Host "Open dashboard: http://localhost:4200/app/dashboard" -ForegroundColor Cyan
