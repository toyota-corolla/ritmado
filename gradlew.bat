@echo off
setlocal
set VERSION=8.9
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%VERSION%-bin\lifeexe
set GRADLE_BIN=%DIST_DIR%\gradle-%VERSION%\bin\gradle.bat
if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%DIST_DIR%\gradle-%VERSION%-bin.zip'; Expand-Archive -Force '%DIST_DIR%\gradle-%VERSION%-bin.zip' '%DIST_DIR%'; Remove-Item '%DIST_DIR%\gradle-%VERSION%-bin.zip'"
)
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
