# PowerShell build script for NimbusFS
$ErrorActionPreference = "Stop"

$mvnVersion = "3.9.6"
$mvnDir = "$PSScriptRoot\.mvn_bin"
$mvnZip = "$PSScriptRoot\.mvn_bin\maven.zip"
$mvnExe = "$mvnDir\apache-maven-$mvnVersion\bin\mvn.cmd"

if (-not (Test-Path $mvnExe)) {
    Write-Host "Downloading Apache Maven $mvnVersion..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $mvnDir | Out-Null
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mvnVersion/apache-maven-$mvnVersion-bin.zip"
    Invoke-WebRequest -Uri $url -OutFile $mvnZip
    Write-Host "Extracting Maven..." -ForegroundColor Cyan
    Expand-Archive -Path $mvnZip -DestinationPath $mvnDir -Force
    Remove-Item $mvnZip -Force
}

Write-Host "Building NimbusFS with Maven..." -ForegroundColor Green
& $mvnExe clean package -DskipTests=false
