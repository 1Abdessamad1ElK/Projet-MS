package ma.mobility.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class GatewayConfig {

    /** Pour la recherche : delais courts, on prefere une reponse degradee a une attente. */
    @Bean("searchRest")
    RestTemplate searchRest(RestTemplateBuilder builder) {
        RestTemplate rt = builder.setConnectTimeout(Duration.ofSeconds(2)).setReadTimeout(Duration.ofSeconds(10)).build();
        rt.setErrorHandler(noThrow());
        return rt;
    }

    /** Pour la reservation : la saga fait plusieurs appels en cascade, delai plus long. */
    @Bean("proxyRest")
    RestTemplate proxyRest(RestTemplateBuilder builder) {
        RestTemplate rt = builder.setConnectTimeout(Duration.ofSeconds(2)).setReadTimeout(Duration.ofSeconds(30)).build();
        rt.setErrorHandler(noThrow());
        return rt;
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build());
    }

    /** On veut lire les codes 4xx/5xx nous-memes (passe-plat des erreurs), pas d'exception. */
    private static DefaultResponseErrorHandler noThrow() {
        return new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        };
    }
}
