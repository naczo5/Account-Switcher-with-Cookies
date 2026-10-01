:: In-Game Account Switcher is a third-party mod for Minecraft Java Edition that
:: allows you to change your logged in account in-game, without restarting it.
::
:: Copyright (C) 2015-2022 The_Fireplace
:: Copyright (C) 2021-2026 VidTu
::
:: This program is free software: you can redistribute it and/or modify
:: it under the terms of the GNU Lesser General Public License as published by
:: the Free Software Foundation, either version 3 of the License, or
:: (at your option) any later version.
::
:: This program is distributed in the hope that it will be useful,
:: but WITHOUT ANY WARRANTY; without even the implied warranty of
:: MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
:: GNU Lesser General Public License for more details.
::
:: You should have received a copy of the GNU Lesser General Public License
:: along with this program.  If not, see <https://www.gnu.org/licenses/>

:: Disable echo.
@echo off
setlocal EnableExtensions EnableDelayedExpansion

:: Repository root (this script lives in tests\).
set "ROOT=%~dp0.."
pushd "%ROOT%" || exit /b 2

:: Resolve a JDK 17+ javac.
set "JAVAC="
for %%C in ("%JDK17_HOME%\bin\javac.exe" "%JAVA_HOME%\bin\javac.exe") do (
    if exist "%%~C" call :checkjavac "%%~C" && goto :havajavac
)
for /d %%D in ("C:\Program Files\Java\jdk-*" "C:\Program Files\Eclipse Adoptium\jdk-*") do (
    if exist "%%~D\bin\javac.exe" call :checkjavac "%%~D\bin\javac.exe" && goto :havajavac
)
where javac >nul 2>&1 && call :checkjavac javac && goto :havajavac
echo run-parser-tests: JDK 17+ required (set JDK17_HOME). 1>&2
popd
exit /b 2

:checkjavac
set "VER="
for /f "tokens=2" %%V in ('"%~1" -version 2^>^&1') do set "VER=%%V"
if not defined VER exit /b 1
for /f "delims=. tokens=1" %%M in ("!VER!") do set "MAJOR=%%M"
if !MAJOR! GEQ 17 set "JAVAC=%~1" & exit /b 0
exit /b 1

:havajavac
:: Resolve compile-only annotation jars from the Gradle cache.
if defined GRADLE_USER_HOME ( set "GUH=%GRADLE_USER_HOME%" ) else ( set "GUH=%USERPROFILE%\.gradle" )
set "ANN="
set "ERRP="
for /r "%GUH%\caches\modules-2" %%F in (annotations-*.jar) do (
    echo "%%~nxF" | findstr /i "sources javadoc" >nul || set "ANN=%%F"
)
for /r "%GUH%\caches\modules-2" %%F in (error_prone_annotations-*.jar) do (
    echo "%%~nxF" | findstr /i "sources javadoc" >nul || set "ERRP=%%F"
)
if not defined ANN goto :nojars
if not defined ERRP goto :nojars

if exist "tests\out" rmdir /s /q "tests\out"
mkdir "tests\out\classes"
"%JAVAC%" -encoding UTF-8 -d "tests\out\classes" -cp "%ANN%;%ERRP%" ^
    "src\_legacy\_shared\ru\vidtu\ias\auth\cookie\CookieParser.java" ^
    "src\_legacy\_shared\ru\vidtu\ias\auth\cookie\CookieEntry.java" ^
    "src\_legacy\_shared\ru\vidtu\ias\utils\exceptions\FriendlyException.java" ^
    "tests\parser\ru\vidtu\ias\tests\CookieParserTest.java" || ( popd & exit /b 1 )

:: Tests resolve .agents/skills examples relative to the repo root.
for %%J in ("%JAVAC%") do set "JAVABIN=%%~dpJjava.exe"
"%JAVABIN%" -cp "tests\out\classes" ru.vidtu.ias.tests.CookieParserTest
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%

:nojars
echo run-parser-tests: annotations jars not found under %GUH% (run any Gradle build once). 1>&2
popd
exit /b 2
