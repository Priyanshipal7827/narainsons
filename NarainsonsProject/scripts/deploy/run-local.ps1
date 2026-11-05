# Builds the app and runs it on Tomcat at http://localhost:8082 (no Eclipse needed).
#   run-local.ps1          build and start; logs show here, Ctrl+C stops
#   run-local.ps1 -Stop    stop a running server
# Set CATALINA_HOME if your Tomcat 10.1 is somewhere else.
param([switch]$Stop, [int]$Port = 8082)

$ErrorActionPreference = "Stop"
$project = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
# Outside target/ so uploaded images survive "mvn clean".
$base = Join-Path $project ".run\tomcat"

$tomcatHome = @(
    $env:CATALINA_HOME,
    "D:\Users\DELL\eclipse-workspace\apache-tomcat-10.1.36",
    "C:\Program Files\Apache Software Foundation\Tomcat 10.1"
) | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\bootstrap.jar")) } | Select-Object -First 1
if (-not $tomcatHome) {
    throw "Tomcat 10.1 not found. Set CATALINA_HOME to your Tomcat folder."
}
$tomcatArgs = @(
    "-Dcatalina.home=$tomcatHome",
    "-Dcatalina.base=$base",
    "-Djava.io.tmpdir=$base\temp",
    "-cp", "$tomcatHome\bin\bootstrap.jar;$tomcatHome\bin\tomcat-juli.jar",
    "org.apache.catalina.startup.Bootstrap"
)

if ($Stop) {
    & java @tomcatArgs stop
    exit $LASTEXITCODE
}

$busy = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
if ($busy) {
    $owner = (Get-Process -Id $busy.OwningProcess -ErrorAction SilentlyContinue).ProcessName
    throw "Port $Port is already in use by $owner (PID $($busy.OwningProcess)). If this is Eclipse's Tomcat, stop it from the Servers tab first."
}

$mysql = Get-Service MySQL80 -ErrorAction SilentlyContinue
if ($mysql -and $mysql.Status -ne "Running") {
    Write-Warning "MySQL80 is stopped, so APIs will fail. In an Administrator terminal run: net start MySQL80"
}

$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvn = Get-ChildItem "$HOME\.m2\wrapper\dists\apache-maven-*\*\bin\mvn.cmd" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mvn) {
    throw "Maven not found. Install Maven 3.9 and add it to PATH."
}

Write-Host "Building..."
& $mvn -B -q -f (Join-Path $project "pom.xml") -DskipTests clean package
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

foreach ($dir in "conf", "logs", "temp", "work", "webapps") {
    New-Item -ItemType Directory -Force -Path (Join-Path $base $dir) | Out-Null
}
$serverXml = Join-Path $base "conf\server.xml"
if (-not (Test-Path $serverXml)) {
    Copy-Item "$tomcatHome\conf\*" (Join-Path $base "conf") -Recurse -Force
    # Shutdown port differs from Eclipse's Tomcat (8005), so both can stay installed.
    $xml = (Get-Content $serverXml -Raw) -replace 'port="8005"', 'port="8015"'
    [System.IO.File]::WriteAllText($serverXml, $xml)
}
# The HTTP port is applied on every run so -Port always takes effect.
$xml = (Get-Content $serverXml -Raw) -replace '(?s)(<Connector port=")\d+(" protocol="HTTP/1\.1")', "`${1}$Port`$2"
[System.IO.File]::WriteAllText($serverXml, $xml)

& robocopy (Join-Path $project "target\NarainsonsProject") (Join-Path $base "webapps\NarainsonsProject") /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "Could not copy the app into Tomcat" }

Write-Host ""
Write-Host "Admin panel:  http://localhost:$Port/NarainsonsProject/admin/login.html"
Write-Host "Health check: http://localhost:$Port/NarainsonsProject/api/health"
Write-Host "Press Ctrl+C to stop."
Write-Host ""
& java @tomcatArgs start
