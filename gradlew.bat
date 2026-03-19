@rem
@rem Copyright 2015 the original author or authors.
@rem SPDX-License-Identifier: Apache-2.0
@rem
@rem Tcosmatic Replay Mod - Gradle Wrapper Script for Windows
@rem

@echo off
setlocal enabledelayedexpansion

rem Tcosmatic Banner
echo ☠️  TCOSMATIC REPLAY MOD - GRADLE BUILDER ☠️
echo 🔥 Building Ultimate Replay Mod for Minecraft 1.21 🔥
echo.

rem Check Java Version
set REQUIRED_JAVA_VERSION=21
set JAVA_VERSION=

for /f tokens^=2-5^ delims=.-_^" %%j in ('java -fullversion 2^>^&1') do (
    set "JAVA_VERSION=%%j"
)

if not defined JAVA_VERSION (
    echo ❌ ERROR: Java not found! Please install Java %REQUIRED_JAVA_VERSION%+
    exit /b 1
)

if %JAVA_VERSION% LSS %REQUIRED_JAVA_VERSION% (
    echo ❌ ERROR: Java %REQUIRED_JAVA_VERSION% or higher is required for Tcosmatic Replay Mod
    echo Current Java version: %JAVA_VERSION%
    echo Please install Java %REQUIRED_JAVA_VERSION%+
    exit /b 1
)

rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx4G" "-XX:MaxMetaspaceSize=512m" "-XX:+HeapDumpOnOutOfMemoryError" "-Dfile.encoding=UTF-8"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
echo.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
echo.
goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

echo.
echo ✅ Build complete! Check build/libs/ folder
