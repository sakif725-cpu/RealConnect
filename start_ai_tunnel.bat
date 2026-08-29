@echo off
title RealConnect AI Server & Global Cloudflare Tunnel
color 0A

echo ================================================================
echo        RealConnect AI Server + Global HTTPS Tunnel
echo ================================================================
echo.

:: 1. Free port 8000 if occupied
echo [1/3] Ensuring Port 8000 is free...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8000" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)

:: 2. Start Cloudflare Tunnel in background
echo [2/3] Starting Cloudflare Tunnel in background...
start "RealConnect Cloudflare Tunnel" /min "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://127.0.0.1:8000

:: 3. Launch Python server in foreground with live streaming logs
echo [3/3] Starting Python AI Server (Live Logs)...
echo ================================================================
echo.
cd /d "d:\Project\message detection\message detection.py"
if exist .venv\Scripts\activate.bat call .venv\Scripts\activate.bat
set PYTHONUNBUFFERED=1
python -u main.py

pause
