@echo off
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME (
  echo [ERROR] JAVA_HOME is not set. Point it to JDK 17 or newer.
  exit /b 1
)

if not exist "data\snapshot.json" (
  if exist "demo\preset_snapshot_20261022.json" (
    if not exist "data" mkdir "data"
    copy /y "demo\preset_snapshot_20261022.json" "data\snapshot.json" >nul
    echo [info] Seeded the demo accounts into data\snapshot.json
  )
)

set JAR=target\supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar
if not exist "%JAR%" (
  echo [info] Building the executable jar...
  call ".\mvnw.cmd" -B --no-transfer-progress clean package -DskipTests
  if errorlevel 1 exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%JAR%" %*
