@echo off
cd /d "%~dp0server"
set "DEMO_NODE=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
if exist "%DEMO_NODE%" (
  "%DEMO_NODE%" server.mjs
) else (
  node server.mjs
)
pause
