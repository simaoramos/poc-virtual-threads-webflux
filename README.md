# POC 01: virtual threads × WebFlux × MVC tradicional

## Objetivo

Comparar o mesmo endpoint, sob a mesma carga de I/O, em três versões:

| Variante | Modelo |
|---|---|
| `mvc-platform` | Spring MVC com threads de plataforma (Tomcat, 200 threads) |
| `mvc-virtual` | Spring MVC com `spring.threads.virtual.enabled=true` |
| `webflux` | Spring WebFlux com WebClient |

O endpoint `GET /quote` faz duas chamadas em sequência a um serviço que leva 100 ms para responder (`/price` e depois `/convert`, que usa o resultado da primeira). Depois loga o resultado e o devolve.

## Stack

Java 25 · Spring Boot 4.2 · Docker Compose · k6 · Actuator/Micrometer

## Como rodar

Pré-requisitos: JDK 25 ou mais recente, Maven, Docker e `jq`. O k6 roda em container.

```bash
./run-load.sh
```

O script compila os módulos, sobe o stub e roda cada variante nos níveis de 100, 1.000 e 5.000 usuários simultâneos, por 1 minuto cada. Antes de cada rodada, a app é reiniciada e aquecida por 20 s, sem medição. O resumo fica em `results/summary.csv` e os dados brutos em `results/raw/`.

Para uma rodada mais curta:

```bash
VARIANTS="mvc-virtual" LEVELS="100" DURATION=20s ./run-load.sh
```

## Decisões para manter a comparação justa

- Cada app roda num container limitado a 2 vCPU e 512 MB. O stub não tem limite e é não bloqueante, para nunca ser o gargalo.
- As duas variantes MVC usam o mesmo código. Só muda a propriedade de virtual threads.
- O pool de conexões do WebClient foi aumentado para 2.000 conexões, com fila de espera ilimitada. O padrão do Reactor Netty é pequeno e derrubaria o WebFlux por falta de conexão, não por modelo de execução. O cliente HTTP do JDK, usado pelo RestClient, não limita conexões.
- As métricas do Actuator ficam numa porta separada (9090). Assim, a coleta não disputa threads com a carga.
- O tracing está ligado (amostragem de 100%) nas duas apps, com o mesmo custo para todas.
- `process.cpu.usage` é a fração de CPU usada pela JVM em relação às CPUs disponíveis no container. `jvm.memory.used` soma heap e não heap.
- `jvm.threads.live` não conta virtual threads, por isso o número de threads não entra na comparação.

## Resultados

Benchmark local, não representa produção.

| Variante | Usuários | req/s | p99 (ms) | Erros (%) | Memória pico (MB) | CPU média (%) |
|---|---|---|---|---|---|---|
| mvc-platform | 100 | | | | | |
| mvc-platform | 1.000 | | | | | |
| mvc-platform | 5.000 | | | | | |
| mvc-virtual | 100 | | | | | |
| mvc-virtual | 1.000 | | | | | |
| mvc-virtual | 5.000 | | | | | |
| webflux | 100 | | | | | |
| webflux | 1.000 | | | | | |
| webflux | 5.000 | | | | | |

| Versão | Tempo para escrever |
|---|---|
| MVC (platform e virtual) | ~15 min (primeiro contato com o RestClient) |
| WebFlux | ~1 h |

Máquina: [CPU, memória, sistema operacional]

## O que aprendi

- [O primeiro tropeço na versão WebFlux e como resolvi]
- [Conclusão tirada dos números]
