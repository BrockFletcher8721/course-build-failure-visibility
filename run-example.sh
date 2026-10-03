#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/learning-release-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" dev.learningops.ReleaseDiagnosticsTest
