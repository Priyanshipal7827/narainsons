# Narainsons API smoke test — PowerShell; JSON bodies file se (curl escaping avoid)
$ErrorActionPreference = "Stop"
$base = "http://localhost:8082/NarainsonsProject"
$dir = Join-Path $PSScriptRoot "payloads"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

function Invoke-Api($Method, $Path, $BodyFile, $Token) {
    $args = @("-s", "-w", "`nHTTP_CODE:%{http_code}", "-X", $Method, "$base$Path", "-H", "Content-Type: application/json")
    if ($Token) { $args += @("-H", "Authorization: Bearer $Token") }
    if ($BodyFile) { $args += @("--data-binary", "@$BodyFile") }
    $raw = & curl.exe @args
    $parts = $raw -split "`nHTTP_CODE:"
    return @{ Body = $parts[0]; Code = $parts[1] }
}

Write-Host "=== Health ===" -ForegroundColor Cyan
$h = Invoke-Api GET "/api/health" $null $null
Write-Host $h.Body

Write-Host "`n=== Login (Web / Postman user) ===" -ForegroundColor Cyan
@'
{"email":"test@example.com","password":"Test@123"}
'@ | Set-Content -Path "$dir\login.json" -Encoding ASCII -NoNewline
$login = Invoke-Api POST "/api/auth/login" "$dir\login.json" $null
Write-Host $login.Body
$token = ($login.Body | ConvertFrom-Json).data.access_token
if (-not $token) { throw "Login fail — DB mein test@example.com + Test@123 hona chahiye" }

Write-Host "`n=== GET /me ===" -ForegroundColor Cyan
$me = Invoke-Api GET "/api/user/me" $null $token
Write-Host $me.Body

Write-Host "`n=== OTP Send + Verify ===" -ForegroundColor Cyan
@'
{"mobile_number":"7776665554"}
'@ | Set-Content -Path "$dir\otp-send.json" -Encoding ASCII -NoNewline
$os = Invoke-Api POST "/api/auth/otp/send" "$dir\otp-send.json" $null
Write-Host $os.Body
$otp = ($os.Body | ConvertFrom-Json).data.dev_otp
if (-not $otp) { Write-Host "WARN: dev_otp nahi — Tomcat restart karke app.dev.expose_otp=true deploy karo" -ForegroundColor Yellow }
else {
    @"
{"mobile_number":"7776665554","otp":"$otp"}
"@ | Set-Content -Path "$dir\otp-verify.json" -Encoding ASCII -NoNewline
    $ov = Invoke-Api POST "/api/auth/otp/verify" "$dir\otp-verify.json" $null
    Write-Host $ov.Body
}

Write-Host "`n=== Onboarding step 1 ===" -ForegroundColor Cyan
@'
{"step":1,"name":"Test User","first_name":"Test","last_name":"User","email":"test@example.com","gender":"male","date_of_birth":"1990-01-15"}
'@ | Set-Content -Path "$dir\onb1.json" -Encoding ASCII -NoNewline
$o1 = Invoke-Api POST "/api/user/onboarding" "$dir\onb1.json" $token
Write-Host $o1.Body

Write-Host "`n=== Sync ===" -ForegroundColor Cyan
@'
{"contacts":[],"gallery":[]}
'@ | Set-Content -Path "$dir\sync.json" -Encoding ASCII -NoNewline
$sy = Invoke-Api POST "/api/user/sync" "$dir\sync.json" $token
Write-Host $sy.Body

Write-Host "`nDone. MySQL: narainson_db.users check karo." -ForegroundColor Green
