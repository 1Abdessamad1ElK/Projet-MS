package ma.mobility.reservations.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import ma.mobility.reservations.saga.Models.Booking;
import ma.mobility.reservations.saga.Models.BridgeUnavailableException;
import ma.mobility.reservations.saga.Models.Hold;
import ma.mobility.reservations.saga.Models.NotAvailableException;
import ma.mobility.reservations.saga.Models.Offer;
import ma.mobility.reservations.saga.Ports.Bridge;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client REST d'un bridge (meme contrat pour rail-bridge, air-bridge et carshare).
 *
 * Resilience (Resilience4j) :
 *   - un CIRCUIT BREAKER par bridge : si le train tombe, l'avion continue de marcher ;
 *   - RETRY seulement pour search et cancel (idempotents). Jamais pour hold/confirm :
 *     rejouer un hold risquerait de bloquer des places en double.
 *   - les erreurs metier (HTTP 409) ne comptent ni comme panne ni comme retry.
 */
public class HttpBridge implements Bridge {

    private final String name;
    private final String baseUrl;
    private final RestTemplate rest;
    private final CircuitBreaker breaker;
    private final Retry retry;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpBridge(String name, String baseUrl, RestTemplate rest, CircuitBreaker breaker, Retry retry) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.rest = rest;
        this.breaker = breaker;
        this.retry = retry;
    }

    @Override
    public List<Offer> search(String origin, String destination, String date) {
        return call(true, () -> {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/v1/offers")
                    .queryParam("origin", origin)
                    .queryParam("destination", destination)
                    .queryParam("date", date)
                    .build().encode().toUri();
            Offer[] offers = rest.getForObject(uri, Offer[].class);
            return offers == null ? List.<Offer>of() : List.of(offers);
        });
    }

    @Override
    public Hold hold(String offerId, int seats) {
        return call(false, () -> rest.postForObject(baseUrl + "/api/v1/holds",
                Map.of("offerId", offerId, "seats", seats), Hold.class));
    }

    @Override
    public Booking confirm(String holdId) {
        return call(false, () -> rest.postForObject(baseUrl + "/api/v1/holds/{holdId}/confirmation",
                null, Booking.class, holdId));
    }

    @Override
    public void cancel(String reference) {
        call(true, () -> {
            rest.postForEntity(baseUrl + "/api/v1/cancellations", Map.of("reference", reference), Void.class);
            return Boolean.TRUE;
        });
    }

    // ------------------------------------------------------------------

    private <T> T call(boolean retryable, Supplier<T> action) {
        Supplier<T> decorated = CircuitBreaker.decorateSupplier(breaker, action);
        if (retryable) {
            decorated = Retry.decorateSupplier(retry, decorated);
        }
        try {
            return decorated.get();
        } catch (HttpClientErrorException.Conflict e) {
            throw new NotAvailableException(detail(e));
        } catch (HttpClientErrorException e) {
            throw new BridgeUnavailableException(name + " bridge rejected the request: " + e.getStatusCode() + " " + detail(e));
        } catch (CallNotPermittedException e) {
            throw new BridgeUnavailableException(name + " bridge circuit is OPEN (calls blocked while it recovers)");
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new BridgeUnavailableException(name + " bridge unavailable: " + e.getMessage());
        }
    }

    /** Extrait "detail" du Problem JSON {code, detail}, sinon renvoie le corps brut. */
    private String detail(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        try {
            JsonNode n = mapper.readTree(body);
            if (n.hasNonNull("detail")) {
                return n.get("detail").asText();
            }
        } catch (Exception ignored) {
            // corps non JSON : on renvoie tel quel
        }
        return body;
    }
}
