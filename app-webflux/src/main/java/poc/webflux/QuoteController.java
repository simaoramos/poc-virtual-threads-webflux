package poc.webflux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@RestController
class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final WebClient stub;

    QuoteController(WebClient stub) {
        this.stub = stub;
    }

    @GetMapping("/quote")
    Mono<Object> quote() {
        return Mono.error(new UnsupportedOperationException("TODO"));
    }
}
