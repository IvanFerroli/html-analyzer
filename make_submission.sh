#!/usr/bin/env bash
set -euo pipefail

if [ "${1-}" = "" ]; then
  echo "Usage: ./make_submission.sh ivanilson_ferreira"
  exit 2
fi

NAME="$1"
OUT="${NAME}.tar.gz"

cd "$(dirname "$0")"

rm -f *.class
javac HtmlAnalyzer.java

# entrega sem diretórios; só arquivos na raiz do tar
tar -czf "$OUT" HtmlAnalyzer.java README.md 2>/dev/null || tar -czf "$OUT" HtmlAnalyzer.java

echo "=== tar contents ==="
tar -tzf "$OUT"
echo "Built: $OUT"
