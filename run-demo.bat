@echo off
chcp 65001 >nul
reg add "HKCU\Console" /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1
cd /d "%~dp0"

echo Building FastFileContentIndex module...
call mvn -q install -DskipTests
if errorlevel 1 (
    echo Failed to build FastFileContentIndex module.
    pause
    exit /b 1
)

echo Running FastFileContentIndex Demo...
cd examples\Demo
call run-demo.bat
cd ..\..
pause
