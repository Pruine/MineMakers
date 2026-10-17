@echo off
setlocal
py -3 "%~dp0tools\desktop_overlay.py" %*
if errorlevel 1 (
    echo.
    echo Read the error above. If Python was not found, install Python 3.10 or newer from python.org.
    pause
)
