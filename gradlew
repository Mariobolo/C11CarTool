#!/bin/sh
# Gradle wrapper launcher (minimal)
# For full wrapper, run: gradle wrapper --gradle-version 7.5.1
APP_HOME=$( cd "${0%/*}" && pwd -P )
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
