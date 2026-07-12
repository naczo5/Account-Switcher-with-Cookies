@echo off
REM Build Minecraft 1.8.9 Forge (requires JDK 8 — set JAVA_HOME or use gradle.properties)
cd /d "%~dp0forge-1.8"
call gradlew.bat build %*
cd /d "%~dp0"
