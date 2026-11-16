@echo off
echo === LOG ===
type "C:\Users\Sasuu\Downloads\glassmorphic-audio-player\_apk_build.log"
echo.
echo === LOG SIZE ===
for %%I in ("C:\Users\Sasuu\Downloads\glassmorphic-audio-player\_apk_build.log") do echo %%~zI
echo === TERM HEAD ===
powershell -NoProfile -Command "Get-Content 'C:\Users\Sasuu\.cursor\projects\c-Users-Sasuu-Downloads-glassmorphic-audio-player\terminals\918241.txt' -TotalCount 12"
echo === TERM TAIL ===
powershell -NoProfile -Command "Get-Content 'C:\Users\Sasuu\.cursor\projects\c-Users-Sasuu-Downloads-glassmorphic-audio-player\terminals\918241.txt' -Tail 8"
echo === JAVA ===
tasklist /FI "IMAGENAME eq java.exe"
echo === GRADLE CACHE ===
dir /b "C:\Users\Sasuu\.gradle\wrapper\dists" 2>nul
dir /s /b "C:\Users\Sasuu\.gradle\wrapper\dists\gradle-9.0-bin\*\gradle-9.0-bin.zip*" 2>nul
