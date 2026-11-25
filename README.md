# POC 01: virtual threads × WebFlux × MVC tradicional

## Objetivo

Comparar o mesmo endpoint, sob a mesma carga de I/O, em cinco variantes:

| Variante | Modelo |
|---|---|
| `mvc-platform` | Spring MVC com threads de plataforma (Tomcat, 200 threads) |
| `mvc-platform-1000` | Igual à anterior, com 1.000 threads: controle para separar o efeito do modelo de thread do efeito do limite de concorrência |
| `mvc-virtual` | Spring MVC com `spring.threads.virtual.enabled=true`, sem limite de concorrência |
| `mvc-virtual-limited` | Igual à anterior, com no máximo 1.000 requisições processadas ao mesmo tempo (com 2.000, o heap de 384 MB estoura) |
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
- As variantes MVC usam o mesmo código. Só mudam as propriedades `VIRTUAL_THREADS` e `MAX_CONCURRENT_REQUESTS`.
- As chamadas simultâneas ao stub são limitadas a 2.000 nas duas apps (`stub.max-concurrency`). No WebFlux, pelo pool do Reactor Netty, cujo padrão é pequeno e derrubaria o WebFlux por falta de conexão, não por modelo de execução. No MVC, por um semáforo no RestClient, porque o cliente HTTP do JDK não limita conexões.
- O limite de requisições da `mvc-virtual-limited` (`ConcurrencyLimitConfig`) envolve o executor de virtual threads do Tomcat com um semáforo. A requisição excedente espera numa virtual thread parada, antes de o Tomcat alocar os buffers de processamento.
- As métricas do Actuator ficam numa porta separada (9090). Assim, a coleta não disputa threads com a carga.
- O tracing está ligado (amostragem de 100%) nas duas apps, com o mesmo custo para todas.
- `process.cpu.usage` é a fração de CPU usada pela JVM em relação às CPUs disponíveis no container. `jvm.memory.used` soma heap e não heap.
- `jvm.threads.live` não conta virtual threads, por isso o número de threads não entra na comparação.

## Resultados

Benchmark local, não representa produção. Máquina: AMD Ryzen 5 8600G (12 threads), 30 GB de RAM, Arch Linux (kernel 7.3), Docker 29, k6 v2.4.0. Cada app limitada a 2 vCPU e 512 MB.

### Rodada completa: 100, 1.000 e 5.000 usuários

| Variante | Usuários | req/s | p99 (ms) | Erros (%) | Memória pico (MB) | CPU média (%) |
|---|---|---|---|---|---|---|
| mvc-platform | 100 | 491 | 250 | 0,00 | 350 | 19,3 |
| mvc-platform | 1.000 | 976 | 1.428 | 0,00 | 437 | 32,1 |
| mvc-platform | 5.000 | 969 | 5.622 | 0,00 | 423 | 31,0 |
| mvc-virtual | 100 | 491 | 231 | 0,00 | 376 | 13,7 |
| mvc-virtual | 1.000 | 3.413 | 493 | 0,00 | 422 | 61,1 |
| mvc-virtual | 5.000 | 473 | 9.878 | 99,83 | — | — |
| webflux | 100 | 496 | 210 | 0,00 | 333 | 12,0 |
| webflux | 1.000 | 3.832 | 461 | 0,00 | 431 | 59,8 |
| webflux | 5.000 | 5.123 | 9.996 | 2,26 | 407 | 88,9 |

Com 5.000 usuários, a `mvc-virtual` caiu por `OutOfMemoryError` (heap). A memória e a CPU dessa linha ficaram de fora: a coleta só registrou 2 amostras antes de a app parar de responder.

Esta rodada foi feita antes do semáforo nas chamadas ao stub no MVC e das variantes `mvc-platform-1000` e `mvc-virtual-limited`.

### 5.000 usuários: o efeito do limite de concorrência

| Variante | Requisições em processamento | req/s | p99 (ms) | Erros (%) | CPU média (%) |
|---|---|---|---|---|---|
| mvc-platform | até 200 (threads) | 949 | 5.937 | 0,00 | 31,5 |
| mvc-platform-1000 | até 1.000 (threads) | 904 | 8.995 | 1,47 | 72,8 |
| mvc-virtual | sem limite | 480 | 10.001 | 94,49 (OOM) | — |
| mvc-virtual-limited | até 1.000 (semáforo) | 2.568 | 2.789 | 0,39 | 60,4 |
| mvc-virtual-limited | até 2.000 (semáforo) | 512 | 10.001 | 86,34 (OOM) | — |
| webflux | sem limite | 5.079 | 10.000 | 1,90 | 88,1 |

Todas as linhas já com o semáforo de 2.000 chamadas ao stub. A `mvc-virtual-limited` com limite de 1.000 rodou duas vezes: 2.419 e 2.568 req/s.

### Esforço de implementação

| Versão | Tempo para escrever |
|---|---|
| MVC (platform e virtual) | ~15 min (primeiro contato com o RestClient) |
| WebFlux | ~1 h |

## O que aprendi

**Virtual threads e limite de concorrência**

- Com 100 usuários, as variantes empatam (~490 req/s). O teto é a latência do stub: 2 chamadas de 100 ms limitam cada usuário a ~5 req/s.
- O pool de 200 threads limita o MVC tradicional a ~1.000 req/s (200 ÷ 0,2 s), e o p99 cresce com a fila. Em troca, ele não cai: o pool é um freio de concorrência que vem de graça.
- Virtual threads tiram esse freio. Com 5.000 usuários, todas as requisições entram em processamento ao mesmo tempo, cada uma com buffers do Tomcat e pilha no heap. O heap de 384 MB estourou (histograma: ~196 MB de `byte[]`, ~82 MB de `char[]`, ~40 MB de `StackChunk`), as threads que aceitam conexões no Tomcat morreram e a app parou de responder.
- Limitar só as chamadas ao stub não resolve: a requisição que espera já alocou buffers e pilha. O limite precisa ficar na entrada, antes de o Tomcat processar a requisição.
- Com o limite de 1.000 no executor de virtual threads, a app fez ~2,7x o throughput do MVC tradicional, com p99 menor e o mesmo código imperativo. Com 2.000, o heap estourou de novo: neste setup, o teto fica entre 1.000 e 2.000.
- Subir o pool de threads de plataforma para 1.000 não ajudou: o throughput ficou igual e a CPU mais que dobrou. Com o mesmo limite, o ganho vem do modelo de thread, não de permitir mais concorrência. A causa da CPU mais alta não foi medida (hipóteses: troca de contexto entre 1.000 threads do sistema operacional e a varredura de 1.000 pilhas pelo coletor de lixo).

**WebFlux**

- Também não tem limite de entrada, mas cada requisição em andamento custa pouco: não tem pilha, e os buffers do Netty ficam fora do heap, em pool. Com 5.000 usuários foi o mais rápido (~5.100 req/s), mas saturou a CPU (~89%), com ~2% de timeouts e p99 de 10 s.
- `block()` na thread do Netty gera `IllegalStateException`. A solução é encadear as chamadas com `flatMap`.
- Erro assíncrono não cai no `try/catch`. A primeira versão respondia 200 com "Bad Gateway" no corpo. A solução é `onErrorResume` devolvendo 502.
- O traceId sumia dos logs. Ele só volta com `spring.reactor.context-propagation: auto`, que tem custo em cada operador.
- Breakpoint na linha da cadeia para na montagem do fluxo, não na chamada. Para depurar, use breakpoint dentro da lambda ou `.log()`.

## Limitações

- O k6, o stub e as apps rodam na mesma máquina e disputam CPU.
- Uma rodada por cenário, exceto onde indicado, sem medir variação.
- `jvm.memory.used` não conta a memória direta do Netty, então a memória do WebFlux está subestimada. O pico de memória também reflete até onde o heap cresce antes de a JVM coletar o lixo, e não a memória realmente necessária.
- O timeout do k6 é de 10 s. Com um timeout menor, a fila do `mvc-platform` com 5.000 usuários (p99 de 5,9 s) apareceria como erro.
