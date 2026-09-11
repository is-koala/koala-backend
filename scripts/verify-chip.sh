#!/usr/bin/env bash
# Cipten okunan SOD ve DG1'i dogrular, hangi adimda takildigini soyler.
#
#   ./scripts/verify-chip.sh sod.bin dg1.bin /opt/koala/csca
#
# Dosyalar ham ikili ya da base64 olabilir, ikisi de tanintir.
set -euo pipefail
cd "$(dirname "$0")/.."
CP_FILE="target/koala-classpath.txt"
[ -f "$CP_FILE" ] || ./mvnw -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
java -cp "$(cat "$CP_FILE")" scripts/ChipDiagnostic.java "$@"
