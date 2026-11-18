#!/usr/bin/env sh
# LIFE.EXE Gradle bootstrap wrapper. It installs the pinned Gradle distribution if absent.
set -eu
VERSION="8.9"
DIST_URL="https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${VERSION}-bin/lifeexe"
GRADLE_BIN="$DIST_DIR/gradle-${VERSION}/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$DIST_DIR"
  ZIP="$DIST_DIR/gradle-${VERSION}-bin.zip"
  echo "LIFE.EXE: baixando Gradle ${VERSION}..."
  if command -v curl >/dev/null 2>&1; then curl -fL --retry 3 "$DIST_URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$DIST_URL"
  else echo "É necessário curl ou wget para instalar o Gradle." >&2; exit 1; fi
  unzip -q -o "$ZIP" -d "$DIST_DIR"
  rm -f "$ZIP"
fi
exec "$GRADLE_BIN" "$@"
