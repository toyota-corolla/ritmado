@echo off
where gradle >nul 2>nul
if %errorlevel%==0 (gradle %*) & exit /b %errorlevel%
echo Gradle 8.9 nao esta instalado. Use o GitHub Actions ou instale Gradle 8.9 para compilar localmente.
exit /b 1
