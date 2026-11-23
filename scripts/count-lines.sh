#!/usr/bin/env bash
# Conta linhas de código (sem linhas vazias, comentários e imports) de cada app.
cd "$(dirname "$0")/.."
for m in app-mvc app-webflux; do
  n=$(find "$m/src/main/java" -name '*.java' -exec cat {} + \
      | grep -vE '^\s*$|^\s*(//|/\*|\*)|^\s*import ' | wc -l)
  echo "$m: $n linhas"
done
