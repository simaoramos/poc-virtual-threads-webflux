package poc.webflux;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

@RestController
class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final WebClient stub;

    QuoteController(WebClient stub) {
        this.stub = stub;
    }

    @GetMapping("/quote")
    Mono<ResponseEntity<Object>> quote() {
        return stub.get()
                .uri("/price")
                .retrieve()
                .bodyToMono(PriceResponse.class)
                //.log("price-response") // loga o fluxo de execução do Mono, útil para debugging
                // a segunda chamada só começa quando o preço chega, e recebe o valor, não o Mono
                .flatMap(price -> stub.get()
                        .uri(uriBuilder -> uriBuilder.path("/convert").queryParam("amount", price.value()).build())
                        .retrieve()
                        .bodyToMono(ConvertResponse.class)
                        //.log("convert-response") // loga o fluxo de execução do Mono, útil para debugging
                        .map(converted -> new QuoteResponse(price.value(), converted.value())))
                .doOnNext(quote -> log.info("quote price={} converted={}", quote.price(), quote.converted()))
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                // erro assíncrono não passa por try/catch: ele chega como sinal no fluxo
                .onErrorResume(QuoteController::isUpstreamFailure, e -> {
                    log.error("Error fetching quote", e);
                    return Mono.just(ResponseEntity.status(502).body(Map.of("error", "Bad Gateway")));
                });
    }

    /** Mesma regra do MVC: falha de I/O (inclui timeout) ou 5xx do stub vira 502. */
    private static boolean isUpstreamFailure(Throwable e) {
        return e instanceof WebClientRequestException
                || (e instanceof WebClientResponseException r && r.getStatusCode().is5xxServerError());
    }

    record PriceResponse(BigDecimal value) {}
    record ConvertResponse(BigDecimal value) {}
    record QuoteResponse(BigDecimal price, BigDecimal converted) {}
}
