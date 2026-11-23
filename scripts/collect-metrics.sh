#!/usr/bin/env bash
# Lê jvm.memory.used e process.cpu.usage do Actuator a cada 1s até ser interrompido.
# Uso: collect-metrics.sh <porta-management> <arquivo.csv>
set -u
port=$1
out=$2
base="http://localhost:${port}/actuator/metrics"
echo "ts,mem_bytes,cpu_usage" > "$out"
while true; do
  mem=$(curl -s --max-time 2 "$base/jvm.memory.used" | jq -r '.measurements[0].value // empty')
  cpu=$(curl -s --max-time 2 "$base/process.cpu.usage" | jq -r '.measurements[0].value // empty')
  [[ -n "$mem" && -n "$cpu" ]] && echo "$(date +%s),$mem,$cpu" >> "$out"
  sleep 1
done
