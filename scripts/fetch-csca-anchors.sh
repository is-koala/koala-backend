#!/usr/bin/env bash
# CSCA guven cipalarini BSI master list'inden cikarir.
#
# Master list ucte bir donemde guncelleniyor; bu betik duzenli calistirilmali.
# Cikti dizini IDENTITY_CSCA_DIRECTORY olarak verilir.
#
#   ./scripts/fetch-csca-anchors.sh /opt/koala/csca TR

set -euo pipefail

OUT_DIR="${1:?kullanim: fetch-csca-anchors.sh <cikti-dizini> [ulke-kodu]}"
COUNTRY="${2:-TR}"
LIST_URL="https://www.bsi.bund.de/SharedDocs/Downloads/DE/BSI/ElekAusweise/CSCA/GermanMasterList.zip?__blob=publicationFile"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Master list indiriliyor..."
curl -fsSL "$LIST_URL" -o "$WORK/ml.zip"
unzip -q -o "$WORK/ml.zip" -d "$WORK"

ML="$(find "$WORK" -name '*.ml' -type f | head -1)"
[ -n "$ML" ] || { echo "master list dosyasi bulunamadi" >&2; exit 1; }

echo "Imzali liste acilyor: $(basename "$ML")"
openssl cms -verify -noverify -inform DER -in "$ML" -out "$WORK/content.der" -outform DER 2>/dev/null

mkdir -p "$OUT_DIR"
python3 - "$WORK/content.der" "$OUT_DIR" "$COUNTRY" <<'PY'
import sys, subprocess, tempfile, os
from pathlib import Path

content, out_dir, country = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]

# CscaMasterList ::= SEQUENCE { version INTEGER, certSet SET OF Certificate }
raw = content.read_bytes()

def parse_len(b, i):
    first = b[i]
    if first < 0x80:
        return first, i + 1
    n = first & 0x7F
    return int.from_bytes(b[i + 1:i + 1 + n], 'big'), i + 1 + n

i = 0
assert raw[i] == 0x30, "beklenen SEQUENCE"
_, i = parse_len(raw, i + 1)
assert raw[i] == 0x02, "beklenen INTEGER (version)"
vlen, j = parse_len(raw, i + 1)
i = j + vlen
assert raw[i] == 0x31, "beklenen SET OF Certificate"
setlen, i = parse_len(raw, i + 1)
end = i + setlen

written = 0
while i < end:
    start = i
    assert raw[i] == 0x30
    clen, j = parse_len(raw, i + 1)
    i = j + clen
    der = raw[start:i]
    subject = subprocess.run(
        ["openssl", "x509", "-inform", "DER", "-noout", "-subject"],
        input=der, capture_output=True).stdout.decode(errors="replace")
    if f"C={country}" in subject or f"C = {country}" in subject:
        target = out_dir / f"csca-{country.lower()}-{written:02d}.der"
        target.write_bytes(der)
        written += 1
        print(f"  {target.name}  {subject.strip()}")

print(f"\n{written} sertifika yazildi -> {out_dir}")
if written == 0:
    raise SystemExit(f"{country} icin sertifika bulunamadi")
PY
