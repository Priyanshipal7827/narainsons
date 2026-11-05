# Compile NarainsonsProject Java sources and copy to Eclipse wtpwebapps (Tomcat hot deploy)
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$repoRoot = Split-Path $projectRoot -Parent
$srcRoot = Join-Path $projectRoot "src\main\java"
$outDir = Join-Path $projectRoot "target\classes"
$libDir = Join-Path $projectRoot "src\main\webapp\WEB-INF\lib"
$wtpClasses = Join-Path $repoRoot ".metadata\.plugins\org.eclipse.wst.server.core\tmp0\wtpwebapps\NarainsonsProject\WEB-INF\classes"

$javacCandidates = @(
  "C:\Users\DELL\Documents\sts-5.2.0.RELEASE\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_25.0.3.v20260502-0818\jre\bin\javac.exe",
  "D:\Users\DELL\eclipse-workspace\apache-tomcat-10.1.36\..\..\..\Documents\sts-5.2.0.RELEASE\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_25.0.3.v20260502-0818\jre\bin\javac.exe"
)
$javac = $javacCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $javac) {
  $javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
}
if (-not $javac) {
  Write-Error "javac not found. STS/Eclipse se Project > Clean / Build karein."
  exit 1
}

# Set CATALINA_HOME to your Tomcat folder.
$tomcatHome = if ($env:CATALINA_HOME) { $env:CATALINA_HOME } else { "D:\Users\DELL\eclipse-workspace\apache-tomcat-10.1.36" }
$tomcatLib = Join-Path $tomcatHome "lib\servlet-api.jar"
if (-not (Test-Path $tomcatLib)) {
  Write-Error "Tomcat servlet-api.jar not found at $tomcatLib"
  exit 1
}

$cp = (Get-ChildItem $libDir -Filter *.jar | ForEach-Object { $_.FullName }) -join ";"
$cp = "$tomcatLib;$cp"

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$javaFiles = Get-ChildItem -Path $srcRoot -Filter *.java -Recurse | ForEach-Object { $_.FullName }
Write-Host "Compiling $($javaFiles.Count) files with $javac ..."
& $javac -encoding UTF-8 --release 17 -cp $cp -d $outDir @javaFiles
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$wtpWebInf = Join-Path $repoRoot ".metadata\.plugins\org.eclipse.wst.server.core\tmp0\wtpwebapps\NarainsonsProject\WEB-INF"
$srcWebXml = Join-Path $projectRoot "src\main\webapp\WEB-INF\web.xml"

if (Test-Path (Split-Path $wtpClasses -Parent)) {
  Write-Host "Copying classes to wtpwebapps..."
  robocopy $outDir $wtpClasses /E /NFL /NDL /NJH /NJS | Out-Null
  if (Test-Path $srcWebXml) {
    Copy-Item -Force $srcWebXml (Join-Path $wtpWebInf "web.xml")
  }
  Write-Host "Deployed: $wtpClasses"
  Write-Host "Tomcat restart ya Eclipse Publish recommended."
} else {
  Write-Host "wtpwebapps not found — output only in target\classes"
}
