package poc.stub;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Serviço "lento" usado como dependência externa. É não bloqueante de propósito,
 * para que o gargalo do teste fique sempre nas apps comparadas, nunca aqui.
 *
 * GET /price            -> {"value": 42.0}
 * GET /convert?amount=n -> {"value": n * 5.0}
 */
@SpringBootApplication
public class StubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StubApplication.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> routes(@Value("${stub.delay-ms}") long delayMs) {
        Duration delay = Duration.ofMillis(delayMs);
        return route()
                .GET("/price", req -> Mono.delay(delay)
                        .then(ServerResponse.ok().bodyValue(Map.of("value", 42.0))))
                .GET("/convert", req -> {
                    double amount = Double.parseDouble(req.queryParam("amount").orElse("0"));
                    return Mono.delay(delay)
                            .then(ServerResponse.ok().bodyValue(Map.of("value", amount * 5.0)));
                })
                .build();
    }
}
