#!/usr/bin/env bash
# SentinelCV Startup Script
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="${PROJECT_ROOT}/.tools/amazon-corretto-21.jdk/Contents/Home"
export PATH="${JAVA_HOME}/bin:${PROJECT_ROOT}/.tools/apache-maven-3.9.6/bin:${PATH}"

echo "=========================================================="
echo " Starting SentinelCV - Offline CV Security Assessment System"
echo " Java Version: $(java -version 2>&1 | head -n 1)"
echo " Project Directory: ${PROJECT_ROOT}"
echo " Web Dashboard: http://localhost:8080"
echo "=========================================================="

exec "${PROJECT_ROOT}/.tools/apache-maven-3.9.6/bin/mvn" spring-boot:run
