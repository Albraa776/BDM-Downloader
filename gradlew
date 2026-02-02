#!/bin/bash
# Gradle Wrapper executable for Unix-like systems
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
warn () { echo "$*" ; }
die () { echo "$*" ; exit 1 ; }
# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
# This is a simplified wrapper for GitHub Actions
exec ./app/gradlew "$@"
