#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script
# ----------------------------------------------------------------------------

set -e

# OS specific support
cygwin=false
darwin=false
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*)  mingw=true ;;
  Darwin*) darwin=true ;;
esac

# Resolve project base directory
BASEDIR="$(cd "$(dirname "$0")" && pwd)"
MAVEN_PROJECTBASEDIR="${BASEDIR}"
export MAVEN_PROJECTBASEDIR

# Set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ]; then
  if [ -d "/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home" ]; then
    JAVA_HOME="/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home"
  elif [ -x "/usr/libexec/java_home" ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null)" || true
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  javaExecutable="$(which java)"
  if [ -n "$javaExecutable" ] && ! [ "$(expr "\"$javaExecutable\"" : '\([^ ]*\)')" = "no" ]; then
    JAVA_HOME="$(cd "$(dirname "$javaExecutable")/.." && pwd)"
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  echo "Error: JAVA_HOME is not defined correctly."
  exit 1
fi

JAVACMD="$JAVA_HOME/bin/java"
if [ ! -x "$JAVACMD" ]; then
  echo "Error: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
  exit 1
fi

# Set Maven home
MAVEN_HOME="${BASEDIR}/.mvn/maven"

# Download Maven if needed
if [ ! -d "$MAVEN_HOME" ]; then
  echo "Downloading Apache Maven 3.9.6..."
  mkdir -p "${BASEDIR}/.mvn"

  MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz"

  if command -v curl > /dev/null 2>&1; then
    curl -fsSL "$MAVEN_URL" -o "${BASEDIR}/.mvn/maven.tar.gz"
  elif command -v wget > /dev/null 2>&1; then
    wget -q "$MAVEN_URL" -O "${BASEDIR}/.mvn/maven.tar.gz"
  else
    echo "Error: Neither curl nor wget found. Cannot download Maven."
    exit 1
  fi

  tar -xzf "${BASEDIR}/.mvn/maven.tar.gz" -C "${BASEDIR}/.mvn"
  mv "${BASEDIR}/.mvn/apache-maven-3.9.6" "$MAVEN_HOME"
  rm -f "${BASEDIR}/.mvn/maven.tar.gz"

  echo "Maven downloaded successfully."
fi

# Execute Maven
exec "$MAVEN_HOME/bin/mvn" \
  -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
  "$@"
