# Saari documented APIs — curl.exe + JSON files
$ErrorActionPreference = "Continue"
$base = "http://localhost:8082/NarainsonsProject"
# Needs: app.dev.expose_otp=true on a LOCAL server, and these env vars from SeedFiveUsers output:
# NARAINSONS_SEED1_PASSWORD, NARAINSONS_SEED3_PASSWORD
$dir = Join-Path $PSScriptRoot "payloads"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

$results = @()
function Log-Api($name, $ok, $detail) {
    $script:results += [pscustomobject]@{ Api = $name; Ok = $ok; Detail = $detail }
    $c = if ($ok) { "Green" } else { "Red" }
    Write-Host ("[{0}] {1} — {2}" -f $(if ($ok) { "OK" } else { "FAIL" }), $name, $detail) -ForegroundColor $c
}

function Post-Json($path, $jsonFile, $token) {
    $args = @("-s", "-X", "POST", "$base$path", "-H", "Content-Type: application/json")
    if ($token) { $args += @("-H", "Authorization: Bearer $token") }
    $args += @("--data-binary", "@$jsonFile")
    return (& curl.exe @args) | ConvertFrom-Json
}

function Get-Api($path, $token) {
    $args = @("-s", "$base$path")
    if ($token) { $args += @("-H", "Authorization: Bearer $token") }
    return (& curl.exe @args) | ConvertFrom-Json
}

# --- Health ---
$h = Get-Api "/api/health" $null
Log-Api "GET /api/health" ($h.success -eq $true) $h.message

# --- User 1 login (seed) ---
(@{ email = "seed1@narainsons.local"; password = $env:NARAINSONS_SEED1_PASSWORD } | ConvertTo-Json -Compress) | Set-Content "$dir\login1.json" -Encoding ASCII -NoNewline
$login = Post-Json "/api/auth/login" "$dir\login1.json" $null
$token = $login.data.access_token
Log-Api "POST /api/auth/login" ($login.success -eq $true) "token=$([bool]$token)"

# --- OTP (extra mobile) ---
@'
{"mobile_number":"9100000099"}
'@ | Set-Content "$dir\otp-send.json" -Encoding ASCII -NoNewline
$os = Post-Json "/api/auth/otp/send" "$dir\otp-send.json" $null
$otp = $os.data.dev_otp
Log-Api "POST /api/auth/otp/send" ($os.success -eq $true) "sms_sent=$($os.data.sms_sent) dev_otp=$([bool]$otp)"
if ($otp) {
    @"
{"mobile_number":"9100000099","otp":"$otp"}
"@ | Set-Content "$dir\otp-verify.json" -Encoding ASCII -NoNewline
    $ov = Post-Json "/api/auth/otp/verify" "$dir\otp-verify.json" $null
    Log-Api "POST /api/auth/otp/verify" ($ov.success -eq $true) "token=$([bool]$ov.data.access_token)"
} else {
    Log-Api "POST /api/auth/otp/verify" $false "dev_otp missing — Tomcat restart (app.dev.expose_otp=true)"
}

# --- Email OTP seed2 ---
@'
{"email":"seed2@narainsons.local"}
'@ | Set-Content "$dir\email-send.json" -Encoding ASCII -NoNewline
$es = Post-Json "/api/auth/email/send" "$dir\email-send.json" $null
$eotp = $es.data.dev_otp
Log-Api "POST /api/auth/email/send" ($es.success -eq $true) "dev_otp=$([bool]$eotp)"
if ($eotp) {
    @"
{"email":"seed2@narainsons.local","otp":"$eotp"}
"@ | Set-Content "$dir\email-verify.json" -Encoding ASCII -NoNewline
    $ev = Post-Json "/api/auth/email/verify" "$dir\email-verify.json" $null
    Log-Api "POST /api/auth/email/verify" ($ev.success -eq $true) "token=$([bool]$ev.data.access_token)"
} else {
    Log-Api "POST /api/auth/email/verify" $false "dev_otp missing"
}

# --- Forgot password seed3 ---
@'
{"email":"seed3@narainsons.local"}
'@ | Set-Content "$dir\forgot-send.json" -Encoding ASCII -NoNewline
$fs = Post-Json "/api/auth/password/forgot/send" "$dir\forgot-send.json" $null
$fotp = $fs.data.dev_otp
Log-Api "POST /api/auth/password/forgot/send" ($fs.success -eq $true) "dev_otp=$([bool]$fotp)"
if ($fotp) {
    @"
{"email":"seed3@narainsons.local","otp":"$fotp","new_password":"$($env:NARAINSONS_SEED3_PASSWORD)"}
"@ | Set-Content "$dir\forgot-reset.json" -Encoding ASCII -NoNewline
    $fr = Post-Json "/api/auth/password/forgot/reset" "$dir\forgot-reset.json" $null
    Log-Api "POST /api/auth/password/forgot/reset" ($fr.success -eq $true -and $fr.data.password_updated) "updated=$($fr.data.password_updated)"
} else {
    Log-Api "POST /api/auth/password/forgot/reset" $false "dev_otp missing"
}

# --- User APIs with seed1 token ---
if ($token) {
    $me = Get-Api "/api/user/me" $token
    Log-Api "GET /api/user/me" ($me.success -eq $true) "id=$($me.data.user.id)"

    @'
{"step":1,"name":"Seed One","first_name":"Seed","last_name":"One","email":"seed1@narainsons.local","gender":"Male","date_of_birth":"1992-06-01"}
'@ | Set-Content "$dir\onb1.json" -Encoding ASCII -NoNewline
    $o1 = Post-Json "/api/user/onboarding" "$dir\onb1.json" $token
    Log-Api "POST /api/user/onboarding step1" ($o1.success -eq $true) $o1.message

    @'
{"step":2,"current_address":"MG Road","current_city":"Jaipur","current_state":"Rajasthan","current_pincode":"302001","employment_type":"Salaried","monthly_income":45000}
'@ | Set-Content "$dir\onb2.json" -Encoding ASCII -NoNewline
    $o2 = Post-Json "/api/user/onboarding" "$dir\onb2.json" $token
    Log-Api "POST /api/user/onboarding step2" ($o2.success -eq $true) $o2.message

    @'
{"step":3,"aadhaar_last4":"4321","pan_last4":"XY12","bank_name":"HDFC","ifsc_code":"HDFC0001234","bank_account_last4":"9876","privacy_consent":true,"terms_accepted":true,"kyc_consent":true}
'@ | Set-Content "$dir\onb3.json" -Encoding ASCII -NoNewline
    $o3 = Post-Json "/api/user/onboarding" "$dir\onb3.json" $token
    Log-Api "POST /api/user/onboarding step3" ($o3.success -eq $true) $o3.message

    @'
{"contacts":[{"name":"Ravi","phone":"9876500000"}],"gallery":[{"uri":"content://media/1"}],"device_id":"seed-device-1","device_type":"Android"}
'@ | Set-Content "$dir\sync.json" -Encoding ASCII -NoNewline
    $sy = Post-Json "/api/user/sync" "$dir\sync.json" $token
    Log-Api "POST /api/user/sync" ($sy.success -eq $true) $sy.message
} else {
    Log-Api "GET /api/user/me" $false "no token"
    Log-Api "POST onboarding/sync" $false "skipped"
}

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
$results | Format-Table -AutoSize
$fail = ($results | Where-Object { -not $_.Ok }).Count
Write-Host "Passed: $($results.Count - $fail) / $($results.Count)"
