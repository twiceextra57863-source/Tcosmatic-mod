#!/bin/sh
#
# Tcosmatic Replay Mod - FIXED Gradle Wrapper Script
#

##############################################################################

echo "☠️  TCOSMATIC REPLAY MOD - GRADLE BUILDER ☠️"
echo "🔥 Building Ultimate Replay Mod for Minecraft 1.21 🔥"
echo ""

# 🔥 FIX: Remove problematic cd command
# Original line 89 was: cd: can't cd to "./

# Better directory handling
PRG="$0"
while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`/"$link"
    fi
done

# Get absolute path
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

# Check Java version
check_java_version() {
    if type -p java > /dev/null 2>&1; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
        echo "✅ Found Java version: $JAVA_VERSION"
        
        if [ "$JAVA_VERSION" -lt 21 ]; then
            echo "❌ ERROR: Java 21+ required! Found Java $JAVA_VERSION"
            exit 1
        fi
    else
        echo "❌ ERROR: Java not found! Please install Java 21+"
        exit 1
    fi
}

# Run Java check
check_java_version

# Set JAVA_HOME if not set (auto-detect common locations)
if [ -z "$JAVA_HOME" ]; then
    echo "🔍 JAVA_HOME not set, auto-detecting..."
    
    # Common Java locations
    if [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
        echo "✅ Found Java at: $JAVA_HOME"
    elif [ -d "/usr/lib/jvm/java-21-oracle" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21-oracle"
        echo "✅ Found Java at: $JAVA_HOME"
    elif [ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
        echo "✅ Found Java at: $JAVA_HOME"
    elif [ -d "C:\\Program Files\\Java\\jdk-21" ]; then
        export JAVA_HOME="C:\\Program Files\\Java\\jdk-21"
        echo "✅ Found Java at: $JAVA_HOME"
    else
        echo "⚠️  Using system Java (JAVA_HOME not set)"
    fi
fi

# Classpath
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Default JVM options - REMOVED problematic JAVA_HOME
DEFAULT_JVM_OPTS='"-Xmx4G" "-XX:MaxMetaspaceSize=512m" "-XX:+HeapDumpOnOutOfMemoryError" "-Dfile.encoding=UTF-8"'

# Execute Gradle
java $DEFAULT_JVM_OPTS -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
