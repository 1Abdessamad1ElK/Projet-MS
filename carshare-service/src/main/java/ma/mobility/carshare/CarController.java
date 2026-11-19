package ma.mobility.carshare;

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

/** API REST canonique (contracts/bridge-api.openapi.yaml). */
@RestController
@RequestMapping("/api/v1")
public class CarController {

    private final CarService service;

    public CarController(CarService service) {
        this.service = service;
    }

    /** origin = ville de retrait ; destination ignoree (voir CarService). */
    @GetMapping("/offers")
    public List<Api.Offer> offers(@RequestParam String origin,
                                  @RequestParam(required = false) String destination,
                                  @RequestParam String date) {
        return service.search(origin, date);
    }

    @PostMapping("/holds")
    public ResponseEntity<Api.Hold> hold(@RequestBody Api.HoldRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.hold(req.offerId(), req.seats()));
    }

    @PostMapping("/holds/{holdId}/confirmation")
    public Api.Booking confirm(@PathVariable String holdId) {
        return service.confirm(holdId);
    }

    @PostMapping("/cancellations")
    public ResponseEntity<Void> cancel(@RequestBody Api.CancelRequest req) {
        service.cancel(req.reference());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CarService.NotAvailableException.class)
    public ResponseEntity<Api.Problem> notAvailable(CarService.NotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Api.Problem("NOT_AVAILABLE", e.getMessage()));
    }
}
