package poc.webflux;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@SpringBootApplication
public class WebfluxApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebfluxApplication.class, args);
    }

    /**
     * O pool padrão do Reactor Netty é pequeno e tem fila de espera limitada.
     * Ajustado aqui para não virar gargalo artificial com 5.000 usuários.
     * O cliente JDK usado no MVC não limita conexões.
     *
     * Os timeouts são lidos das propriedades stub.* e aplicados aqui.
     */
    @Bean
    WebClient stubClient(WebClient.Builder builder,
                         @Value("${stub.base-url}") String baseUrl,
                         @Value("${stub.connect-timeout}") Duration connectTimeout,
                         @Value("${stub.read-timeout}") Duration readTimeout) {
        ConnectionProvider pool = ConnectionProvider.builder("stub")
                .maxConnections(2000)
                .pendingAcquireMaxCount(-1)
                .build();
        HttpClient httpClient = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(readTimeout);
        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
