#!/bin/sh

#
# Gradle start up script
#

APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () { echo "$*"; } >&2
die () { echo; echo "$*"; echo; exit 1; } >&2

OS_SPECIFIC=""
case "$(uname)" in
  CYGWIN*) OS_SPECIFIC=cygwin;;
  Darwin*) OS_SPECIFIC=darwin;;
  MSYS*|MINGW*) OS_SPECIFIC=msys;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1 ; then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
    fi
fi

# Collect all arguments
eval set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "\"-Dorg.gradle.appname=$APP_BASE_NAME\"" -classpath "\"$CLASSPATH\"" org.gradle.wrapper.GradleWrapperMain "$@"

exec "$JAVACMD" "$@"
