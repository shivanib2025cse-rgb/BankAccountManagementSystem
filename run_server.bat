@echo off
setlocal enabledelayedexpansion
title NovaBank - Web Backend Server
cd /d "%~dp0"

echo =========================================================
echo    NOVABANK - BANK ACCOUNT MANAGEMENT SYSTEM
echo    Web Backend Server & Modern UI Dashboard
echo =========================================================
echo.

:: Detect Java Compiler
set "JAVAC_CMD=javac"
set "JAVA_CMD=java"

if exist "lib\oracleJdk-27\bin\javac.exe" (
    set "JAVAC_CMD=%~dp0lib\oracleJdk-27\bin\javac.exe"
    set "JAVA_CMD=%~dp0lib\oracleJdk-27\bin\java.exe"
    echo [INFO] Using embedded Oracle JDK 27 from project lib directory.
) else (
    echo [INFO] Using system default Java compiler.
)

echo.
echo [1/2] Compiling all Java backend source files...
"%JAVAC_CMD%" --release 25 -encoding UTF-8 -cp "lib/mysql-connector-j-8.3.0.jar;." *.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed! Please verify that JDK is properly installed.
    pause
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Backend compiled successfully.
echo.
echo [2/2] Launching Web Server on http://localhost:8080 ...
echo [INFO] Opening default web browser in 2 seconds...
start "" cmd /c "timeout /t 2 /nobreak >nul & start http://localhost:8080"

"%JAVA_CMD%" -cp ".;lib/mysql-connector-j-8.3.0.jar" BankServer

pause
