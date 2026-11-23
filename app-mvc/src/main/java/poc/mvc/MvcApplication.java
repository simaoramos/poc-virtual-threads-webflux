package poc.mvc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * A mesma app roda em duas variantes, só mudando uma propriedade:
 * mvc-platform (spring.threads.virtual.enabled=false) e mvc-virtual (=true).
 */
@SpringBootApplication
public class MvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(MvcApplication.class, args);
    }

    /** Usa o builder do Boot para herdar observabilidade (propagação do trace). */
    @Bean
    RestClient stubClient(RestClient.Builder builder, @Value("${stub.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
