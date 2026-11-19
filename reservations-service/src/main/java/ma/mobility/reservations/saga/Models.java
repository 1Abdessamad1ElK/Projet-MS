package ma.mobility.reservations.saga;

import java.util.ArrayList;
import java.util.List;

/**
 * Modele du domaine "reservation" + modele canonique cote client des bridges.
 * Classes simples a champs publics : serialisables en JSON sans configuration.
 */
public final class Models {
    private Models() {}

    // ---------------- Modele canonique (vu depuis les bridges) ----------------

    public record Offer(String offerId, String mode, String origin, String destination,
                        String departure, String arrival, long priceMinor,
                        String currency, int seatsAvailable) {}

    public record Hold(String holdId, String offerId, String mode) {}

    public record Booking(String bookingRef, String holdId, String mode) {}

    public record Quote(String quoteId, long subtotalMinor, int discountPercent,
                        long discountMinor, long totalMinor, String currency) {}

    public record QuoteLine(String mode, String offerId, long priceMinor, int quantity) {}

    // ---------------- Requete ----------------

    /** Un maillon demande par le voyageur (origin/destination/date servent a valider l'offre et son prix). */
    public record LegRequest(String mode, String offerId, int seats,
                             String origin, String destination, String date) {}

    // ---------------- Domaine ----------------

    public static final class Leg {
        public String mode;
        public String offerId;
        public int seats;
        public String origin;
        public String destination;
        public String date;
        public long priceMinor;
        public String holdId;
        public String bookingRef;
        /** PENDING, HELD, CONFIRMED, CANCELLED, FAILED, CANCEL_FAILED */
        public String status = "PENDING";
    }

    public static final class Reservation {
        public String id;
        public String idempotencyKey;
        public String customerEmail;
        /** PENDING, CONFIRMED, FAILED (compense), NEEDS_ATTENTION (compensation incomplete) */
        public String status = "PENDING";
        public String failureReason;
        public long totalMinor;
        public String currency = "MAD";
        public String createdAt;
        public List<Leg> legs = new ArrayList<>();
        /** Journal de la saga, ligne par ligne (utile en demo et en soutenance). */
        public List<String> log = new ArrayList<>();
    }

    // ---------------- Erreurs ----------------

    /** Erreur metier d'un bridge (HTTP 409) : plus de place, hold expire... */
    public static class NotAvailableException extends RuntimeException {
        public NotAvailableException(String message) { super(message); }
    }

    /** Bridge/service injoignable ou circuit ouvert. */
    public static class BridgeUnavailableException extends RuntimeException {
        public BridgeUnavailableException(String message) { super(message); }
    }
}
