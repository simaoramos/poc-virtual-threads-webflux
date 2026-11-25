package poc.mvc;

import java.util.concurrent.Semaphore;

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

    /**
     * Usa o builder do Boot para herdar observabilidade (propagação do trace).
     *
     * O cliente HTTP do JDK não limita conexões. Com threads de plataforma, o pool
     * do Tomcat (200) já segura a concorrência. Com virtual threads esse freio some,
     * e 5.000 chamadas simultâneas ao stub estouram o heap. O semáforo aplica o mesmo
     * limite do pool do WebClient (stub.max-concurrency), e quem passar dele espera.
     */
    @Bean
    RestClient stubClient(RestClient.Builder builder,
                          @Value("${stub.base-url}") String baseUrl,
                          @Value("${stub.max-concurrency}") int maxConcurrency) {
        Semaphore permits = new Semaphore(maxConcurrency, true);
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    permits.acquireUninterruptibly();
                    try {
                        return execution.execute(request, body);
                    } finally {
                        permits.release();
                    }
                })
                .build();
    }
}
