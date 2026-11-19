package ma.mobility.gateway;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AGREGATION : interroge EN PARALLELE les trois mondes (train/CORBA, vol/SOAP, voiture/REST)
 * via leurs bridges, et fusionne le tout. Le client ne voit qu'un seul format (modele canonique).
 *
 * Mode degrade : si un bridge est lent ou en panne (circuit breaker), sa partie revient vide
 * avec degraded=true, au lieu de faire echouer toute la recherche.
 */
@Service
public class SearchService {

    public record ModeResult(List<JsonNode> offers, boolean degraded, String error) {}

    private final RestTemplate rest;
    private final CircuitBreakerRegistry breakers;
    private final String railUrl;
    private final String airUrl;
    private final String carUrl;

    public SearchService(@Qualifier("searchRest") RestTemplate rest,
                         CircuitBreakerRegistry breakers,
                         @Value("${upstream.rail-url}") String railUrl,
                         @Value("${upstream.air-url}") String airUrl,
                         @Value("${upstream.car-url}") String carUrl) {
        this.rest = rest;
        this.breakers = breakers;
        this.railUrl = railUrl;
        this.airUrl = airUrl;
        this.carUrl = carUrl;
    }

    /**
     * Sans "via" : train et vol origin->destination.
     * Avec "via"  : train origin->via, puis vol via->destination (trajet type train + avion).
     * La voiture est retiree a la ville d'arrivee (destination).
     */
    public Map<String, Object> search(String origin, String destination, String date, String via) {
        final String trainTo = (via == null || via.isBlank()) ? destination : via;
        final String flightFrom = (via == null || via.isBlank()) ? origin : via;

        CompletableFuture<ModeResult> train = CompletableFuture.supplyAsync(
                () -> fetch("rail", railUrl, origin, trainTo, date));
        CompletableFuture<ModeResult> flight = CompletableFuture.supplyAsync(
                () -> fetch("air", airUrl, flightFrom, destination, date));
        CompletableFuture<ModeResult> car = CompletableFuture.supplyAsync(
                () -> fetch("carshare", carUrl, destination, destination, date));

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("origin", origin);
        query.put("via", via);
        query.put("destination", destination);
        query.put("date", date);
        result.put("query", query);
        result.put("train", train.join());
        result.put("flight", flight.join());
        result.put("car", car.join());
        return result;
    }

    private ModeResult fetch(String name, String baseUrl, String origin, String destination, String date) {
        CircuitBreaker cb = breakers.circuitBreaker(name);
        try {
            return CircuitBreaker.decorateSupplier(cb, () -> get(baseUrl, origin, destination, date)).get();
        } catch (Exception e) {
            return new ModeResult(List.of(), true, name + " unavailable: " + e.getMessage());
        }
    }

    private ModeResult get(String baseUrl, String origin, String destination, String date) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/v1/offers")
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .queryParam("date", date)
                .build().encode().toUri();
        ResponseEntity<JsonNode> resp = rest.exchange(uri, HttpMethod.GET, null, JsonNode.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("HTTP " + resp.getStatusCode().value());
        }
        List<JsonNode> offers = new ArrayList<>();
        for (JsonNode n : resp.getBody()) {
            offers.add(n);
        }
        return new ModeResult(offers, false, null);
    }
}
