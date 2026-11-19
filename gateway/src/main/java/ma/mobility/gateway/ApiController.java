package ma.mobility.gateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * API UNIFIEE (BFF) exposee a l'application cliente :
 *   GET  /api/v1/search?origin=&destination=&date=[&via=]   recherche multimodale agregee
 *   POST /api/v1/reservations                               reservation (saga) - header Idempotency-Key conseille
 *   GET  /api/v1/reservations[/{id}]                        suivi des dossiers
 *   GET  /api/v1/notifications                              notifications envoyees
 * Le client ne connait ni CORBA, ni SOAP, ni les services internes.
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final SearchService search;
    private final RestTemplate rest;
    private final String reservationsUrl;
    private final String notificationsUrl;

    public ApiController(SearchService search,
                         @Qualifier("proxyRest") RestTemplate rest,
                         @Value("${upstream.reservations-url}") String reservationsUrl,
                         @Value("${upstream.notifications-url}") String notificationsUrl) {
        this.search = search;
        this.rest = rest;
        this.reservationsUrl = reservationsUrl;
        this.notificationsUrl = notificationsUrl;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String origin,
                                      @RequestParam String destination,
                                      @RequestParam String date,
                                      @RequestParam(required = false) String via) {
        return search.search(origin, destination, date, via);
    }

    @PostMapping("/reservations")
    public ResponseEntity<String> createReservation(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody String body) {
        return forward(HttpMethod.POST, reservationsUrl + "/api/v1/reservations", body, idempotencyKey);
    }

    @GetMapping("/reservations")
    public ResponseEntity<String> listReservations() {
        return forward(HttpMethod.GET, reservationsUrl + "/api/v1/reservations", null, null);
    }

    @GetMapping("/reservations/{id}")
    public ResponseEntity<String> getReservation(@PathVariable String id) {
        return forward(HttpMethod.GET, reservationsUrl + "/api/v1/reservations/" + id, null, null);
    }

    @GetMapping("/notifications")
    public ResponseEntity<String> notifications() {
        return forward(HttpMethod.GET, notificationsUrl + "/api/v1/notifications", null, null);
    }

    /** Passe-plat : code HTTP et corps du service interne sont renvoyes tels quels (409 compris). */
    private ResponseEntity<String> forward(HttpMethod method, String url, String body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        try {
            ResponseEntity<String> resp = rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
            return ResponseEntity.status(resp.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.getBody());
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":\"UPSTREAM_UNAVAILABLE\",\"detail\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
