package ma.mobility.reservations;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import ma.mobility.reservations.infra.HttpBridge;
import ma.mobility.reservations.infra.HttpPricing;
import ma.mobility.reservations.infra.JpaReservationStore;
import ma.mobility.reservations.infra.RabbitEventPublisher;
import ma.mobility.reservations.infra.ReservationJpaRepository;
import ma.mobility.reservations.saga.Ports.Bridge;
import ma.mobility.reservations.saga.SagaOrchestrator;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class AppConfig {

    /** Timeouts explicites : un appel sans delai d'attente peut bloquer toute la saga. */
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .ignoreExceptions(HttpClientErrorException.class) // 409 = erreur metier, pas une panne
                .build();
        return CircuitBreakerRegistry.of(cfg);
    }

    @Bean
    RetryRegistry retryRegistry() {
        RetryConfig cfg = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(300))
                .retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build();
        return RetryRegistry.of(cfg);
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(RabbitEventPublisher.EXCHANGE);
    }

    @Bean
    SagaOrchestrator sagaOrchestrator(RestTemplate rest,
                                      CircuitBreakerRegistry cbs,
                                      RetryRegistry retries,
                                      RabbitTemplate rabbit,
                                      ReservationJpaRepository jpa,
                                      @Value("${bridges.rail-url}") String railUrl,
                                      @Value("${bridges.air-url}") String airUrl,
                                      @Value("${bridges.car-url}") String carUrl,
                                      @Value("${pricing.url}") String pricingUrl,
                                      @Value("${saga.max-confirm-window-seconds}") long windowSeconds) {
        Map<String, Bridge> bridges = new LinkedHashMap<>();
        bridges.put("TRAIN", new HttpBridge("rail", railUrl, rest, cbs.circuitBreaker("rail"), retries.retry("rail")));
        bridges.put("FLIGHT", new HttpBridge("air", airUrl, rest, cbs.circuitBreaker("air"), retries.retry("air")));
        bridges.put("CAR", new HttpBridge("carshare", carUrl, rest, cbs.circuitBreaker("carshare"), retries.retry("carshare")));
        return new SagaOrchestrator(bridges,
                new HttpPricing(pricingUrl, rest),
                new JpaReservationStore(jpa),
                new RabbitEventPublisher(rabbit),
                System::currentTimeMillis,
                windowSeconds * 1000);
    }
}
