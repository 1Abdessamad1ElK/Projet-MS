package ma.mobility.railbridge;

public final class Errors {
    private Errors() {}

    /** Erreur metier : CORBA NotAvailable -> HTTP 409. */
    public static class NotAvailableException extends RuntimeException {
        public NotAvailableException(String message) { super(message); }
    }

    /** Operateur injoignable (ORB, IOR, reseau) -> HTTP 503. */
    public static class UpstreamUnavailableException extends RuntimeException {
        public UpstreamUnavailableException(String message) { super(message); }
    }
}
