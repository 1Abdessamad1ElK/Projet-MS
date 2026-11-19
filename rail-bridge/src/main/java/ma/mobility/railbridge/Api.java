package ma.mobility.railbridge;

/**
 * Modele canonique (voir contracts/bridge-api.openapi.yaml).
 * Les clients de ce bridge ne voient JAMAIS les types CORBA (Trip, NotAvailable...).
 */
public final class Api {
    private Api() {}

    public static class Offer {
        public String offerId;
        public String mode;
        public String origin;
        public String destination;
        public String departure;
        public String arrival;
        public long priceMinor;
        public String currency;
        public int seatsAvailable;
    }

    public static class HoldRequest {
        public String offerId;
        public int seats;
    }

    public static class Hold {
        public String holdId;
        public String offerId;
        public String mode;
        public Hold() {}
        public Hold(String holdId, String offerId, String mode) {
            this.holdId = holdId; this.offerId = offerId; this.mode = mode;
        }
    }

    public static class Booking {
        public String bookingRef;
        public String holdId;
        public String mode;
        public Booking() {}
        public Booking(String bookingRef, String holdId, String mode) {
            this.bookingRef = bookingRef; this.holdId = holdId; this.mode = mode;
        }
    }

    public static class CancelRequest {
        public String reference;
    }

    public static class Problem {
        public String code;
        public String detail;
        public Problem() {}
        public Problem(String code, String detail) { this.code = code; this.detail = detail; }
    }
}
