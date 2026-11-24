package poc.mvc;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@RestController
class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final RestClient stub;

    QuoteController(RestClient stub) {
        this.stub = stub;
    }

    @GetMapping("/quote")
    ResponseEntity<Map<String, String>> quote() {
        try {
            PriceResponse price = stub.get()
                .uri("/price")
                .retrieve()
                .body(PriceResponse.class);

            ConvertResponse converted = stub.get()
                .uri(uriBuilder -> uriBuilder.path("/convert").queryParam("amount", price.value()).build())
                .retrieve()
                .body(ConvertResponse.class);
            
            log.info("quote price={} converted={}", price.value(), converted.value());

            return ResponseEntity.ok(Map.of("price", price.value().toString(), "converted", converted.value().toString()));
        } catch (ResourceAccessException | HttpServerErrorException e) {
            log.error("Error fetching quote", e);
            return ResponseEntity.status(502).body(Map.of("error", "Bad Gateway"));
        }
    }

    record PriceResponse(BigDecimal value) {}
    record ConvertResponse(BigDecimal value) {}
}
