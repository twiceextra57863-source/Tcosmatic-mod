@rem
@rem Tcosmatic Replay Mod - FIXED Gradle Wrapper for Windows
@rem

@echo off
setlocal enabledelayedexpansion

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

echo ✅ Found Java version: %JAVA_VERSION%

if %JAVA_VERSION% LSS %REQUIRED_JAVA_VERSION% (
    echo ❌ ERROR: Java %REQUIRED_JAVA_VERSION%+ required! Found Java %JAVA_VERSION%
    exit /b 1
)

rem Auto-detect JAVA_HOME if not set
if "%JAVA_HOME%"=="" (
    echo 🔍 JAVA_HOME not set, checking common locations...
    
    if exist "C:\Program Files\Java\jdk-21" (
        set JAVA_HOME=C:\Program Files\Java\jdk-21
        echo ✅ Found Java at: !JAVA_HOME!
    ) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21" (
        set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
        echo ✅ Found Java at: !JAVA_HOME!
    ) else if exist "C:\Program Files\Amazon Corretto\jdk21" (
        set JAVA_HOME=C:\Program Files\Amazon Corretto\jdk21
        echo ✅ Found Java at: !JAVA_HOME!
    ) else (
        echo ⚠️  Using system Java (JAVA_HOME not set)
    )
)

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

set DEFAULT_JVM_OPTS=-Xmx4G -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

if %ERRORLEVEL% equ 0 (
    echo ✅ Build complete!
) else (
    echo ❌ Build failed with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
