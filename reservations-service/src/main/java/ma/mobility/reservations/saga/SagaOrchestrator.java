package ma.mobility.reservations.saga;

import ma.mobility.reservations.saga.Models.Hold;
import ma.mobility.reservations.saga.Models.Leg;
import ma.mobility.reservations.saga.Models.LegRequest;
import ma.mobility.reservations.saga.Models.Offer;
import ma.mobility.reservations.saga.Models.Quote;
import ma.mobility.reservations.saga.Models.QuoteLine;
import ma.mobility.reservations.saga.Models.Reservation;
import ma.mobility.reservations.saga.Ports.Bridge;
import ma.mobility.reservations.saga.Ports.EventPublisher;
import ma.mobility.reservations.saga.Ports.Pricing;
import ma.mobility.reservations.saga.Ports.ReservationStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * ORCHESTRATEUR de la saga de reservation multimodale (une saga qui PILOTE, vs choregraphie).
 *
 *   Phase 0  validation + tarification   (aucun effet de bord -> rien a compenser)
 *   Phase 1  HOLD de chaque maillon       (echec -> cancel des holds deja obtenus)
 *   Phase 2  CONFIRM de chaque maillon    (echec -> cancel des bookings confirmes ET des holds restants)
 *
 * Garanties :
 *   - issue coherente : tout confirme, ou rien (coherence "a terme" via compensation, pas ACID) ;
 *   - idempotence : meme Idempotency-Key => meme reservation, aucun nouvel appel aux operateurs ;
 *   - cancel idempotent : rejouer une compensation est le cas normal (retries) ;
 *   - fenetre de confirmation : on ne confirme pas si les holds approchent de leur expiration (10 min).
 */
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final Map<String, Bridge> bridges;
    private final Pricing pricing;
    private final ReservationStore store;
    private final EventPublisher events;
    private final LongSupplier clockMillis;
    private final long maxConfirmWindowMillis;

    public SagaOrchestrator(Map<String, Bridge> bridges, Pricing pricing, ReservationStore store,
                            EventPublisher events, LongSupplier clockMillis, long maxConfirmWindowMillis) {
        this.bridges = bridges;
        this.pricing = pricing;
        this.store = store;
        this.events = events;
        this.clockMillis = clockMillis;
        this.maxConfirmWindowMillis = maxConfirmWindowMillis;
    }

    public Optional<Reservation> find(String id) {
        return store.findById(id);
    }

    public List<Reservation> findAll() {
        return store.findAll();
    }

    public Reservation book(String customerEmail, List<LegRequest> legRequests, String idempotencyKey) {
        // --- Idempotence : meme cle => on renvoie l'existant, sans rien rejouer ---
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Reservation> existing = store.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotency-Key {} deja vue -> reservation {}", idempotencyKey, existing.get().id);
                return existing.get();
            }
        }

        validateRequest(customerEmail, legRequests);

        Reservation r = new Reservation();
        r.id = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        r.idempotencyKey = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey;
        r.customerEmail = customerEmail;
        r.createdAt = Instant.now().toString();
        for (LegRequest lr : legRequests) {
            Leg leg = new Leg();
            leg.mode = lr.mode();
            leg.offerId = lr.offerId();
            leg.seats = lr.seats();
            leg.origin = lr.origin();
            leg.destination = lr.destination();
            leg.date = lr.date();
            r.legs.add(leg);
        }
        step(r, "SAGA started with " + r.legs.size() + " leg(s)");
        store.save(r);
        events.publish("reservation.requested", r, "Reservation " + r.id + " received");

        // --- Phase 0 : validation des offres + tarification (sans effet de bord) ---
        try {
            validateAndPrice(r);
        } catch (RuntimeException e) {
            return fail(r, "Validation/pricing failed: " + e.getMessage());
        }

        // --- Phase 1 : HOLD ---
        long firstHoldAt = -1;
        for (Leg leg : r.legs) {
            try {
                Hold h = bridge(leg).hold(leg.offerId, leg.seats);
                leg.holdId = h.holdId();
                leg.status = "HELD";
                if (firstHoldAt < 0) {
                    firstHoldAt = clockMillis.getAsLong();
                }
                step(r, "HOLD ok " + leg.mode + " " + leg.offerId + " -> " + leg.holdId);
                store.save(r);
                events.publish("reservation.leg.held", r, leg.mode + " " + leg.offerId + " held");
            } catch (RuntimeException e) {
                leg.status = "FAILED";
                step(r, "HOLD FAILED " + leg.mode + " " + leg.offerId + ": " + e.getMessage());
                compensate(r);
                return fail(r, "Hold failed on " + leg.mode + " " + leg.offerId + ": " + e.getMessage());
            }
        }

        // --- Phase 2 : CONFIRM ---
        for (Leg leg : r.legs) {
            if (clockMillis.getAsLong() - firstHoldAt > maxConfirmWindowMillis) {
                step(r, "Confirm window exceeded: holds are about to expire");
                compensate(r);
                return fail(r, "Confirmation window exceeded (holds about to expire)");
            }
            try {
                leg.bookingRef = bridge(leg).confirm(leg.holdId).bookingRef();
                leg.status = "CONFIRMED";
                step(r, "CONFIRM ok " + leg.mode + " " + leg.offerId + " -> " + leg.bookingRef);
                store.save(r);
            } catch (RuntimeException e) {
                // leg.status reste HELD : la compensation annulera ce hold ET les legs deja confirmes
                step(r, "CONFIRM FAILED " + leg.mode + " " + leg.offerId + ": " + e.getMessage());
                compensate(r);
                return fail(r, "Confirm failed on " + leg.mode + " " + leg.offerId + ": " + e.getMessage());
            }
        }

        r.status = "CONFIRMED";
        step(r, "SAGA completed: all legs confirmed, total " + r.totalMinor + " " + r.currency + " (minor units)");
        store.save(r);
        events.publish("reservation.confirmed", r, "Reservation " + r.id + " confirmed, total " + r.totalMinor + " centimes " + r.currency);
        return r;
    }

    // ------------------------------------------------------------------

    private void validateRequest(String customerEmail, List<LegRequest> legs) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("customerEmail is required");
        }
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("at least one leg is required");
        }
        for (LegRequest l : legs) {
            if (l.mode() == null || !bridges.containsKey(l.mode())) {
                throw new IllegalArgumentException("unknown mode '" + l.mode() + "' (expected " + bridges.keySet() + ")");
            }
            if (l.offerId() == null || l.offerId().isBlank()) {
                throw new IllegalArgumentException("offerId is required");
            }
            if (l.seats() < 1) {
                throw new IllegalArgumentException("seats must be >= 1");
            }
            if (l.origin() == null || l.destination() == null || l.date() == null) {
                throw new IllegalArgumentException("origin, destination and date are required (used to re-check the offer price)");
            }
        }
    }

    /** On ne fait JAMAIS confiance au prix envoye par le client : on le relit aupres du bridge. */
    private void validateAndPrice(Reservation r) {
        List<QuoteLine> lines = new ArrayList<>();
        for (Leg leg : r.legs) {
            Offer match = null;
            for (Offer o : bridge(leg).search(leg.origin, leg.destination, leg.date)) {
                if (o.offerId().equals(leg.offerId)) {
                    match = o;
                    break;
                }
            }
            if (match == null) {
                throw new Models.NotAvailableException("offer " + leg.offerId + " not found for "
                        + leg.origin + "->" + leg.destination + " on " + leg.date);
            }
            leg.priceMinor = match.priceMinor();
            lines.add(new QuoteLine(leg.mode, leg.offerId, leg.priceMinor, leg.seats));
        }
        Quote q = pricing.quote(lines);
        r.totalMinor = q.totalMinor();
        r.currency = q.currency();
        step(r, "PRICED " + q.quoteId() + ": subtotal=" + q.subtotalMinor() + " discount=" + q.discountPercent()
                + "% total=" + q.totalMinor() + " " + q.currency());
        store.save(r);
    }

    /**
     * COMPENSATION, dans l'ordre inverse. Pour chaque maillon : si CONFIRMED on annule la reservation
     * ferme (bookingRef), si HELD on libere le maintien (holdId). Les bridges rendent cancel
     * idempotent, et les retries (Resilience4j) rejouent l'appel en cas de panne reseau.
     */
    private void compensate(Reservation r) {
        step(r, "COMPENSATION started");
        for (int i = r.legs.size() - 1; i >= 0; i--) {
            Leg leg = r.legs.get(i);
            boolean confirmed = "CONFIRMED".equals(leg.status);
            boolean held = "HELD".equals(leg.status);
            if (!confirmed && !held) {
                continue; // jamais maintenu : rien a annuler
            }
            String reference = confirmed ? leg.bookingRef : leg.holdId;
            try {
                bridge(leg).cancel(reference);
                leg.status = "CANCELLED";
                step(r, "CANCEL ok " + leg.mode + " " + reference);
            } catch (RuntimeException e) {
                leg.status = "CANCEL_FAILED";
                step(r, "CANCEL FAILED " + leg.mode + " " + reference + ": " + e.getMessage() + " (manual action required)");
                log.error("Compensation failed for reservation {} leg {} ref {}", r.id, leg.mode, reference, e);
            }
        }
        store.save(r);
    }

    private Reservation fail(Reservation r, String reason) {
        boolean incomplete = r.legs.stream().anyMatch(l -> "CANCEL_FAILED".equals(l.status));
        r.status = incomplete ? "NEEDS_ATTENTION" : "FAILED";
        r.failureReason = reason;
        step(r, "SAGA ended: " + r.status + " - " + reason);
        store.save(r);
        events.publish("reservation.failed", r, "Reservation " + r.id + " failed and was rolled back: " + reason);
        return r;
    }

    private Bridge bridge(Leg leg) {
        return bridges.get(leg.mode);
    }

    private void step(Reservation r, String line) {
        r.log.add(Instant.now() + " " + line);
        log.info("[{}] {}", r.id, line);
    }
}
