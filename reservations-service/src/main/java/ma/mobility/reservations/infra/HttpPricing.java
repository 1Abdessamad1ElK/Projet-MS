package ma.mobility.reservations.infra;

import ma.mobility.reservations.saga.Models.BridgeUnavailableException;
import ma.mobility.reservations.saga.Models.Quote;
import ma.mobility.reservations.saga.Models.QuoteLine;
import ma.mobility.reservations.saga.Ports.Pricing;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/** Appel synchrone au pricing-service. */
public class HttpPricing implements Pricing {

    private final String baseUrl;
    private final RestTemplate rest;

    public HttpPricing(String baseUrl, RestTemplate rest) {
        this.baseUrl = baseUrl;
        this.rest = rest;
    }

    @Override
    public Quote quote(List<QuoteLine> lines) {
        try {
            Quote q = rest.postForObject(baseUrl + "/api/v1/quotes", Map.of("lines", lines), Quote.class);
            if (q == null) {
                throw new BridgeUnavailableException("pricing-service returned an empty quote");
            }
            return q;
        } catch (RestClientException e) {
            throw new BridgeUnavailableException("pricing-service unavailable: " + e.getMessage());
        }
    }
}
