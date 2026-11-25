@echo off
cd /d "%~dp0"
if not exist out mkdir out
if not exist data mkdir data
javac -d out src\*.java
if errorlevel 1 (
  echo COMPILE ERROR - tingnan ang output sa itaas.
  pause
  exit /b 1
)
echo Naka-on na ang Media Player.
echo Buksan: http://localhost:8090/
java -cp out MediaPlayer
pause