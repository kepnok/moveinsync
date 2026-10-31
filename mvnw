#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
export JAVA_HOME="${JAVA_HOME:-$DIR/.tools/jdk-21.0.13+9}"
export PATH="$JAVA_HOME/bin:$DIR/.tools/apache-maven-3.9.6/bin:$PATH"
exec mvn "$@"

