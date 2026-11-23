#!/usr/bin/env bash
# Roda o k6 em cada variante e nível de carga e grava results/summary.csv.
# Cada combinação começa com a app recém-iniciada e um aquecimento de 20s (não medido).
#
# Variáveis opcionais:
#   VARIANTS="mvc-platform mvc-virtual webflux"  LEVELS="100 1000 5000"  DURATION=1m
set -euo pipefail
cd "$(dirname "$0")"

VARIANTS=${VARIANTS:-"mvc-platform mvc-virtual webflux"}
LEVELS=${LEVELS:-"100 1000 5000"}
DURATION=${DURATION:-1m}
K6_IMAGE=${K6_IMAGE:-grafana/k6:latest}
declare -A MGMT=([mvc-platform]=9092 [mvc-virtual]=9093 [webflux]=9094)

mkdir -p results/raw
mvn -q -DskipTests package
docker compose build -q
docker compose up -d stub

k6() { # $1 variante, $2 usuários, $3 duração, demais args extras
  local v=$1 vus=$2 dur=$3; shift 3
  docker run --rm --network poc01_default \
    -v "$PWD/load:/scripts:ro" -v "$PWD/results/raw:/out" \
    "$K6_IMAGE" run --quiet \
    -e TARGET="http://$v:8080/quote" -e VUS="$vus" -e DURATION="$dur" \
    "$@" /scripts/quote.js > /dev/null
}

summary=results/summary.csv
echo "variant,vus,rps,p99_ms,error_pct,mem_peak_mb,cpu_avg_pct" > "$summary"

for v in $VARIANTS; do
  for vus in $LEVELS; do
    echo ">> $v com $vus usuários"
    docker compose up -d --force-recreate "$v" > /dev/null
    until curl -sf "http://localhost:${MGMT[$v]}/actuator/health" > /dev/null; do sleep 1; done

    k6 "$v" 50 20s || true   # aquecimento do JIT

    scripts/collect-metrics.sh "${MGMT[$v]}" "results/raw/$v-$vus-metrics.csv" &
    collector=$!
    k6 "$v" "$vus" "$DURATION" --summary-export "/out/$v-$vus.json" || true
    kill "$collector"

    json="results/raw/$v-$vus.json"
    rps=$(jq '.metrics.http_reqs.rate' "$json")
    p99=$(jq '.metrics.http_req_duration["p(99)"]' "$json")
    err=$(jq '.metrics.http_req_failed.value * 100' "$json")
    read -r mem cpu < <(awk -F, 'NR>1 { if ($2>m) m=$2; c+=$3; n++ }
      END { printf "%.0f %.1f\n", m/1048576, (n ? c/n*100 : 0) }' "results/raw/$v-$vus-metrics.csv")
    printf "%s,%s,%.0f,%.0f,%.2f,%s,%s\n" "$v" "$vus" "$rps" "$p99" "$err" "$mem" "$cpu" | tee -a "$summary"

    docker compose stop "$v" > /dev/null
  done
done

docker compose down > /dev/null
echo
column -s, -t < "$summary"
