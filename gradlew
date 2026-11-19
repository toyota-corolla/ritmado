#!/usr/bin/env sh
# Local bootstrap launcher. GitHub Actions installs Gradle 8.9 with setup-gradle.
set -eu
GRADLE_VERSION=8.9
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap"
DIST="$BASE/gradle-$GRADLE_VERSION"
ZIP="$BASE/gradle-$GRADLE_VERSION-bin.zip"
mkdir -p "$BASE"
if [ ! -x "$DIST/bin/gradle" ]; then
  echo "Baixando Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"; else wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"; fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$DIST/bin/gradle" "$@"
