package poc.mvc;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Limita quantas requisições o Tomcat processa ao mesmo tempo com virtual threads.
 *
 * Com threads de plataforma, server.tomcat.threads.max (200) já faz esse papel.
 * Com virtual threads, o Boot troca o executor por um sem limite, e cada requisição
 * em andamento carrega buffers e pilha no heap. Aqui o executor é envolvido por um
 * semáforo: a requisição excedente espera numa virtual thread parada (barata),
 * antes de o Tomcat alocar os buffers de processamento.
 *
 * app.max-concurrent-requests = 0 desliga o limite.
 */
@Configuration
class ConcurrencyLimitConfig {

    @Bean
    WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> virtualThreadConcurrencyLimit(
            @Value("${app.max-concurrent-requests}") int maxConcurrentRequests) {
        return new LimitCustomizer(maxConcurrentRequests);
    }

    /** Roda depois do customizer do Boot que instala o executor de virtual threads (ordem 1). */
    private record LimitCustomizer(int maxConcurrentRequests)
            implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>, Ordered {

        @Override
        public void customize(ConfigurableTomcatWebServerFactory factory) {
            if (maxConcurrentRequests <= 0) {
                return;
            }
            factory.addProtocolHandlerCustomizers(handler -> {
                if (handler.getExecutor() instanceof VirtualThreadExecutor virtual) {
                    handler.setExecutor(new LimitedExecutor(virtual, maxConcurrentRequests));
                }
            });
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    private static final class LimitedExecutor implements Executor {

        private final Executor delegate;
        private final Semaphore permits;

        LimitedExecutor(Executor delegate, int maxConcurrent) {
            this.delegate = delegate;
            this.permits = new Semaphore(maxConcurrent, true);
        }

        @Override
        public void execute(Runnable task) {
            delegate.execute(() -> {
                permits.acquireUninterruptibly();
                try {
                    task.run();
                } finally {
                    permits.release();
                }
            });
        }
    }
}
