package ma.mobility.pricing;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Service de tarification : calcule le prix d'un trajet multimodal. */
@RestController
@RequestMapping("/api/v1")
public class PricingController {

    public record QuoteRequest(List<PricingRules.Line> lines) {}

    public record Quote(String quoteId, long subtotalMinor, int discountPercent,
                        long discountMinor, long totalMinor, String currency) {}

    private final QuoteRepository quotes;

    public PricingController(QuoteRepository quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/quotes")
    public ResponseEntity<Quote> create(@RequestBody QuoteRequest req) {
        PricingRules.Result r = PricingRules.compute(req.lines());
        QuoteEntity e = quotes.save(new QuoteEntity("Q-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), r, "MAD"));
        return ResponseEntity.status(HttpStatus.CREATED).body(toQuote(e));
    }

    @GetMapping("/quotes/{id}")
    public ResponseEntity<Quote> get(@PathVariable String id) {
        return quotes.findById(id).map(e -> ResponseEntity.ok(toQuote(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "detail", String.valueOf(e.getMessage())));
    }

    private static Quote toQuote(QuoteEntity e) {
        return new Quote(e.getQuoteId(), e.getSubtotalMinor(), e.getDiscountPercent(),
                e.getDiscountMinor(), e.getTotalMinor(), e.getCurrency());
    }
}
