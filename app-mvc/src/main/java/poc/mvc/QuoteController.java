package poc.mvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final RestClient stub;

    QuoteController(RestClient stub) {
        this.stub = stub;
    }

    @GetMapping("/quote")
    Object quote() {
        throw new UnsupportedOperationException("TODO");
    }
}
