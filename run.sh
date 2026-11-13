#!/usr/bin/env bash
# SentinelCV Startup Script
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "${PROJECT_ROOT}/.tools/amazon-corretto-21.jdk/Contents/Home" ]; then
    export JAVA_HOME="${PROJECT_ROOT}/.tools/amazon-corretto-21.jdk/Contents/Home"
    export PATH="${JAVA_HOME}/bin:${PROJECT_ROOT}/.tools/apache-maven-3.9.6/bin:${PATH}"
elif [ -d "/opt/homebrew/opt/openjdk@21" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi

MVN_BIN="mvn"
if [ -x "${PROJECT_ROOT}/.tools/apache-maven-3.9.6/bin/mvn" ]; then
    MVN_BIN="${PROJECT_ROOT}/.tools/apache-maven-3.9.6/bin/mvn"
fi

echo "=========================================================="
echo " Starting SentinelCV - Offline CV Security Assessment System"
echo " Java Version: $(java -version 2>&1 | head -n 1)"
echo " Project Directory: ${PROJECT_ROOT}"
echo " Web Dashboard: http://localhost:8080"
echo "=========================================================="

exec "${MVN_BIN}" spring-boot:run
