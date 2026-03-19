#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# SPDX-License-Identifier: Apache-2.0
#
# Tcosmatic Replay Mod - Gradle Wrapper Script
#

##############################################################################
#
#   Tcosmatic Replay Mod - Gradle start up script for POSIX
#
##############################################################################

# Set minimum required Java version
REQUIRED_JAVA_VERSION=21

# Check Java version
check_java_version() {
    if type -p java > /dev/null 2>&1; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
        if [ "$JAVA_VERSION" -lt "$REQUIRED_JAVA_VERSION" ]; then
            echo "❌ ERROR: Java $REQUIRED_JAVA_VERSION or higher is required for Tcosmatic Replay Mod"
            echo "Current Java version: $JAVA_VERSION"
            echo "Please install Java $REQUIRED_JAVA_VERSION+"
            exit 1
        fi
    fi
}

# Run Java version check
check_java_version

# Add Tcosmatic banner
echo "☠️  TCOSMATIC REPLAY MOD - GRADLE BUILDER ☠️"
echo "🔥 Building Ultimate Replay Mod for Minecraft 1.21 🔥"
echo ""

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase maximum file descriptors if possible on macOS
if [ "$(uname)" = "Darwin" ] && [ "$MAX_FD" = "maximum" ] ; then
    MAX_FD=$(ulimit -H -n)
    if [ "$MAX_FD" = "unlimited" ] || [ -z "$MAX_FD" ] ; then
        MAX_FD=$(ulimit -n)
    fi
fi

# Increase maximum file descriptors if possible
if [ "$MAX_FD" != "maximum" ] ; then
    MAX_FD=$(ulimit -n)
    if [ "$MAX_FD" = "unlimited" ] ; then
        MAX_FD=65536
    fi
fi

# Setup project directory
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")"/$link"
    fi
done
SAVED="$(pwd)"
cd "$(dirname \"$PRG\")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

# Add default JVM options here
DEFAULT_JVM_OPTS='"-Xmx4G" "-XX:MaxMetaspaceSize=512m" "-XX:+HeapDumpOnOutOfMemoryError"'

# Collect all arguments for the java command
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Setup classpath
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase maximum file descriptors if possible on macOS
if [ "$(uname)" = "Darwin" ] ; then
    MAX_FD_LIMIT=$(sysctl -n kern.maxfilesperproc)
    if [ "$MAX_FD" -gt "$MAX_FD_LIMIT" ] ; then
        MAX_FD="$MAX_FD_LIMIT"
    fi
fi

# Build classpath
APP_CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Add default JVM options
DEFAULT_JVM_OPTS='"-Xmx4G" "-XX:MaxMetaspaceSize=512m" "-XX:+HeapDumpOnOutOfMemoryError" "-Dfile.encoding=UTF-8"'

# Collect all arguments
APP_ARGS=$(printf "%s " "$@")

# Start the JVM
eval set -- $DEFAULT_JVM_OPTS -cp "$APP_CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$APP_ARGS"
exec "$JAVACMD" "$@"
