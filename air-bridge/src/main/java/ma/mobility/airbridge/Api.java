package ma.mobility.airbridge;

/** Modele canonique (contracts/bridge-api.openapi.yaml). Aucun type SOAP n'est expose. */
public final class Api {
    private Api() {}

    public record Offer(String offerId, String mode, String origin, String destination,
                        String departure, String arrival, long priceMinor,
                        String currency, int seatsAvailable) {}

    public record HoldRequest(String offerId, int seats) {}

    public record Hold(String holdId, String offerId, String mode) {}

    public record Booking(String bookingRef, String holdId, String mode) {}

    public record CancelRequest(String reference) {}

    public record Problem(String code, String detail) {}
}
