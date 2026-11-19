package ma.mobility.carshare;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Logique metier de l'autopartage. Meme cycle de vie que les deux legacy :
 * search -> hold -> confirm / cancel, hold expirant apres 10 min, cancel IDEMPOTENT.
 *
 * Note offre : pour l'autopartage, "origin" = ville de retrait du vehicule ;
 * "destination" est ignoree (le vehicule est pris et rendu au meme endroit).
 */
@Service
@Transactional
public class CarService {

    static final long HOLD_TTL_SECONDS = 600;
    /** Vehicule de demonstration : confirm echoue toujours (simule un echec de paiement). */
    static final String FAULTY_OFFER = "CAR-PAR-FAULTY";

    public static class NotAvailableException extends RuntimeException {
        public NotAvailableException(String message) { super(message); }
    }

    private final VehicleRepository vehicles;
    private final CarHoldRepository holds;

    public CarService(VehicleRepository vehicles, CarHoldRepository holds) {
        this.vehicles = vehicles;
        this.holds = holds;
    }

    public List<Api.Offer> search(String city, String date) {
        sweepExpired();
        return vehicles.findByCityIgnoreCase(city.trim()).stream()
                .map(v -> new Api.Offer(v.getOfferId(), "CAR", v.getCity(), v.getCity(),
                        date + "T08:00:00", date + "T20:00:00",
                        v.getPricePerDayMinor(), "MAD", v.getUnits()))
                .toList();
    }

    public Api.Hold hold(String offerId, int seats) {
        sweepExpired();
        if (seats < 1) {
            throw new NotAvailableException("seats must be >= 1");
        }
        Vehicle v = vehicles.lockById(offerId)
                .orElseThrow(() -> new NotAvailableException("Unknown offer " + offerId));
        if (v.getUnits() < seats) {
            throw new NotAvailableException("Only " + v.getUnits() + " unit(s) left for " + offerId);
        }
        v.setUnits(v.getUnits() - seats);
        CarHold h = new CarHold("CHOLD-" + shortId(), offerId, seats,
                Instant.now().plusSeconds(HOLD_TTL_SECONDS));
        holds.save(h);
        return new Api.Hold(h.getHoldId(), offerId, "CAR");
    }

    /** noRollbackFor : en cas d'expiration on libere le stock AVANT de lever l'erreur. */
    @Transactional(noRollbackFor = NotAvailableException.class)
    public Api.Booking confirm(String holdId) {
        CarHold h = holds.findById(holdId)
                .orElseThrow(() -> new NotAvailableException("Unknown hold " + holdId));
        if (h.getStatus() == CarHold.Status.CONFIRMED) {
            return new Api.Booking(h.getBookingRef(), holdId, "CAR"); // rejeu idempotent
        }
        if (h.getStatus() == CarHold.Status.CANCELLED) {
            throw new NotAvailableException("Hold " + holdId + " was cancelled or expired");
        }
        if (h.getExpiresAt().isBefore(Instant.now())) {
            release(h);
            throw new NotAvailableException("Hold " + holdId + " expired");
        }
        if (FAULTY_OFFER.equals(h.getOfferId())) {
            // le hold reste HELD : c'est a la saga de le compenser
            throw new NotAvailableException("Simulated payment failure for " + FAULTY_OFFER);
        }
        h.setStatus(CarHold.Status.CONFIRMED);
        h.setBookingRef("CBOOK-" + shortId());
        return new Api.Booking(h.getBookingRef(), holdId, "CAR");
    }

    /** Accepte un holdId OU un bookingRef. Inconnu ou deja annule : aucune erreur. */
    public void cancel(String reference) {
        CarHold h = holds.findById(reference)
                .or(() -> holds.findByBookingRef(reference))
                .orElse(null);
        if (h == null || h.getStatus() == CarHold.Status.CANCELLED) {
            return;
        }
        release(h);
    }

    private void release(CarHold h) {
        vehicles.lockById(h.getOfferId()).ifPresent(v -> v.setUnits(v.getUnits() + h.getUnits()));
        h.setStatus(CarHold.Status.CANCELLED);
    }

    private void sweepExpired() {
        holds.findByStatusAndExpiresAtBefore(CarHold.Status.HELD, Instant.now()).forEach(this::release);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
