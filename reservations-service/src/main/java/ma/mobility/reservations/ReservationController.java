package ma.mobility.reservations;

import ma.mobility.reservations.saga.Models.LegRequest;
import ma.mobility.reservations.saga.Models.Reservation;
import ma.mobility.reservations.saga.SagaOrchestrator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API des dossiers de voyage.
 *   POST /api/v1/reservations  -> 201 si tout est confirme, 409 si la saga a echoue (et compense)
 *   Header optionnel "Idempotency-Key" : rejouer la meme requete ne cree pas de doublon.
 */
@RestController
@RequestMapping("/api/v1")
public class ReservationController {

    public record ReservationRequest(String customerEmail, List<LegRequest> legs) {}

    private final SagaOrchestrator saga;

    public ReservationController(SagaOrchestrator saga) {
        this.saga = saga;
    }

    @PostMapping("/reservations")
    public ResponseEntity<Reservation> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ReservationRequest req) {
        Reservation r = saga.book(req.customerEmail(), req.legs(), idempotencyKey);
        HttpStatus status = "CONFIRMED".equals(r.status) ? HttpStatus.CREATED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(r);
    }

    @GetMapping("/reservations/{id}")
    public ResponseEntity<Reservation> get(@PathVariable String id) {
        return saga.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/reservations")
    public List<Reservation> list() {
        return saga.findAll();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "detail", String.valueOf(e.getMessage())));
    }
}
