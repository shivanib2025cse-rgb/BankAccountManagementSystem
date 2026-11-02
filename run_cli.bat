@echo off
setlocal enabledelayedexpansion
title Bank Account Management System - Console CLI
cd /d "%~dp0"

echo =========================================================
echo    BANK ACCOUNT MANAGEMENT SYSTEM - CONSOLE CLI
echo =========================================================
echo.

set "JAVAC_CMD=javac"
set "JAVA_CMD=java"

if exist "lib\oracleJdk-27\bin\javac.exe" (
    set "JAVAC_CMD=%~dp0lib\oracleJdk-27\bin\javac.exe"
    set "JAVA_CMD=%~dp0lib\oracleJdk-27\bin\java.exe"
)

echo [Compiling Java classes...]
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/mysql-connector-j-8.3.0.jar;." *.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo [Running Console Interface...]
"%JAVA_CMD%" -cp ".;lib/mysql-connector-j-8.3.0.jar" Main

pause
