# NovaBank Web Backend & UI Launcher for PowerShell
Set-Location $PSScriptRoot

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "   NOVABANK - BANK ACCOUNT MANAGEMENT SYSTEM" -ForegroundColor Yellow
Write-Host "   Web Backend Server & Modern UI Dashboard" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host ""

$javac = "javac"
$java = "java"

if (Test-Path "$PSScriptRoot\lib\oracleJdk-27\bin\javac.exe") {
    $javac = "$PSScriptRoot\lib\oracleJdk-27\bin\javac.exe"
    $java = "$PSScriptRoot\lib\oracleJdk-27\bin\java.exe"
    Write-Host "[INFO] Using embedded Oracle JDK 27 from project lib directory." -ForegroundColor Green
} else {
    Write-Host "[INFO] Using system default Java environment." -ForegroundColor Gray
}

Write-Host "`n[1/2] Compiling all Java backend source files..." -ForegroundColor Yellow
& $javac --release 25 -encoding UTF-8 -cp "lib/mysql-connector-j-8.3.0.jar;." *.java

if ($LASTEXITCODE -ne 0) {
    Write-Host "`n[ERROR] Compilation failed! Please verify Java installation." -ForegroundColor Red
    pause
    exit $LASTEXITCODE
}

Write-Host "[SUCCESS] Backend compiled successfully." -ForegroundColor Green
Write-Host "`n[2/2] Launching Web Server on http://localhost:8080 ..." -ForegroundColor Yellow
Start-Process "http://localhost:8080"

& $java -cp ".;lib/mysql-connector-j-8.3.0.jar" BankServer
