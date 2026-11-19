package ma.mobility.airbridge;

public final class Errors {
    private Errors() {}

    /** SOAP Fault du partenaire (ex: plus de places) -> HTTP 409. */
    public static class NotAvailableException extends RuntimeException {
        public NotAvailableException(String message) { super(message); }
    }

    /** Partenaire injoignable ou reponse inexploitable -> HTTP 503. */
    public static class UpstreamUnavailableException extends RuntimeException {
        public UpstreamUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
