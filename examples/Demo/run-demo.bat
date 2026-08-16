@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
reg add "HKCU\Console" /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1

echo Building and running FastFileContentIndex Demo...

cd /d "%~dp0"
call mvn clean compile dependency:build-classpath -Dmdep.outputFile=cp.txt -DincludeScope=runtime -q
if %ERRORLEVEL% NEQ 0 ( echo [ERROR] Compile failed. & pause & exit /b %ERRORLEVEL% )

call mvn -q compile exec:java "-Dexec.mainClass=fastfilecontentindex.Demo"
