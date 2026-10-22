@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

rem Resolve a JDK 17+ without requiring the user to set JAVA_HOME.
rem Order: JAVA_HOME, java on PATH, then common install locations.

set "JAVA_EXE="

call :probe_home "%JAVA_HOME%"

if not defined JAVA_EXE (
  for /f "delims=" %%W in ('where java.exe 2^>nul') do (
    if not defined JAVA_EXE call :probe_exe "%%W"
  )
)

if not defined JAVA_EXE (
  for /d %%D in ("%LOCALAPPDATA%\pdc-build-tools\jdk-*") do if not defined JAVA_EXE call :probe_home "%%~fD"
)
if not defined JAVA_EXE (
  for /d %%D in ("%TEMP%\p1-jdk*") do (
    for /d %%E in ("%%~fD\jdk*") do if not defined JAVA_EXE call :probe_home "%%~fE"
  )
)
if not defined JAVA_EXE (
  for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if not defined JAVA_EXE call :probe_home "%%~fD"
)
if not defined JAVA_EXE (
  for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if not defined JAVA_EXE call :probe_home "%%~fD"
)
if not defined JAVA_EXE (
  for /d %%D in ("%USERPROFILE%\.jdks\*") do if not defined JAVA_EXE call :probe_home "%%~fD"
)

if not defined JAVA_EXE (
  echo [ERROR] No JDK 17 or newer found.
  echo         Set JAVA_HOME to a full JDK, or add its bin directory to PATH.
  exit /b 1
)

echo [info] Using Java: %JAVA_HOME%

if not exist "data\snapshot.json" (
  if exist "demo\preset_snapshot_20261022.json" (
    if not exist "data" mkdir "data"
    copy /y "demo\preset_snapshot_20261022.json" "data\snapshot.json" >nul
    echo [info] Seeded the demo accounts into data\snapshot.json
  )
)

set "JAR=target\supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar"
if not exist "%JAR%" (
  echo [info] Building the executable jar...
  call ".\mvnw.cmd" -B --no-transfer-progress clean package -DskipTests
  if errorlevel 1 exit /b 1
)

"%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar "%JAR%" %*
exit /b %ERRORLEVEL%

:probe_home
if "%~1"=="" exit /b 0
if not exist "%~1\bin\java.exe" exit /b 0
call :probe_exe "%~1\bin\java.exe"
exit /b 0

:probe_exe
if "%~1"=="" exit /b 0
if not exist "%~1" exit /b 0
if not exist "%~dp1javac.exe" exit /b 0
set "VER="
set "MAJOR="
set "VF=%TEMP%\p1-java-version.txt"
"%~1" -version > "%VF%" 2>&1
for /f "tokens=3" %%v in ('findstr /i "version" "%VF%"') do if not defined VER set "VER=%%~v"
del "%VF%" >nul 2>nul
if not defined VER exit /b 0
for /f "tokens=1 delims=." %%m in ("!VER!") do set "MAJOR=%%m"
if not defined MAJOR exit /b 0
if !MAJOR! LSS 17 exit /b 0
set "JAVA_EXE=%~1"
for %%H in ("%~dp1..") do set "JAVA_HOME=%%~fH"
exit /b 0
