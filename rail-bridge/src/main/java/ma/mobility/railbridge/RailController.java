package ma.mobility.railbridge;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API REST canonique du bridge train (contracts/bridge-api.openapi.yaml). */
@RestController
@RequestMapping("/api/v1")
public class RailController {

    private final RailCorbaClient rail;

    public RailController(RailCorbaClient rail) {
        this.rail = rail;
    }

    @GetMapping("/offers")
    public List<Api.Offer> offers(@RequestParam String origin,
                                  @RequestParam String destination,
                                  @RequestParam String date) {
        return rail.search(origin, destination, date);
    }

    @PostMapping("/holds")
    public ResponseEntity<Api.Hold> hold(@RequestBody Api.HoldRequest req) {
        String holdId = rail.hold(req.offerId, req.seats);
        return ResponseEntity.status(HttpStatus.CREATED).body(new Api.Hold(holdId, req.offerId, "TRAIN"));
    }

    @PostMapping("/holds/{holdId}/confirmation")
    public Api.Booking confirm(@PathVariable String holdId) {
        return new Api.Booking(rail.confirm(holdId), holdId, "TRAIN");
    }

    @PostMapping("/cancellations")
    public ResponseEntity<Void> cancel(@RequestBody Api.CancelRequest req) {
        rail.cancel(req.reference);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(Errors.NotAvailableException.class)
    public ResponseEntity<Api.Problem> notAvailable(Errors.NotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Api.Problem("NOT_AVAILABLE", e.getMessage()));
    }

    @ExceptionHandler(Errors.UpstreamUnavailableException.class)
    public ResponseEntity<Api.Problem> unavailable(Errors.UpstreamUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Api.Problem("UPSTREAM_UNAVAILABLE", e.getMessage()));
    }
}
