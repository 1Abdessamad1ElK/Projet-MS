import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LOT 0 - Client de test SOAP (partenaire aerien).
 *
 * Aucun Maven, aucune dependance : on envoie du XML SOAP "a la main" avec
 * le HttpClient du JDK. C'est volontairement simple pour COMPRENDRE le
 * protocole. Le vrai bridge (Lot 1) utilisera Spring WS ou JAX-WS.
 *
 * Lancer (JDK 17+), depuis ce dossier :
 *     java AirClient.java
 *
 * Variable optionnelle : AIR_URL (defaut http://localhost:8081/ws)
 */
public class AirClient {

    static final String URL = System.getenv().getOrDefault("AIR_URL", "http://localhost:8081/ws");
    static final String NS = "http://mobility.ma/air";
    static final HttpClient HTTP = HttpClient.newHttpClient();

    /** Erreur SOAP (Fault) renvoyee par le partenaire, ex: plus de places. */
    static class SoapFault extends Exception {
        SoapFault(String message) { super(message); }
    }

    // ------------------------------------------------------------------
    // Les 4 operations du contrat (air.xsd)
    // ------------------------------------------------------------------

    static List<String> searchFlights(String origin, String destination, String date) throws Exception {
        String xml = call("<air:searchFlightsRequest>"
                + "<air:origin>" + origin + "</air:origin>"
                + "<air:destination>" + destination + "</air:destination>"
                + "<air:date>" + date + "</air:date>"
                + "</air:searchFlightsRequest>");
        List<String> flights = new ArrayList<>();
        for (String block : allBlocks(xml, "flight")) {
            flights.add(tag(block, "flightId") + "  " + tag(block, "origin") + " -> "
                    + tag(block, "destination") + "  " + tag(block, "departure")
                    + "  prix=" + tag(block, "priceMinor") + " centimes"
                    + "  places=" + tag(block, "seatsAvailable"));
        }
        return flights;
    }

    static String holdFlight(String flightId, int seats) throws Exception {
        String xml = call("<air:holdFlightRequest>"
                + "<air:flightId>" + flightId + "</air:flightId>"
                + "<air:seats>" + seats + "</air:seats>"
                + "</air:holdFlightRequest>");
        return tag(xml, "holdId");
    }

    static String confirmFlight(String holdId) throws Exception {
        String xml = call("<air:confirmFlightRequest>"
                + "<air:holdId>" + holdId + "</air:holdId>"
                + "</air:confirmFlightRequest>");
        return tag(xml, "bookingRef");
    }

    static String cancelFlight(String reference) throws Exception {
        String xml = call("<air:cancelFlightRequest>"
                + "<air:reference>" + reference + "</air:reference>"
                + "</air:cancelFlightRequest>");
        return tag(xml, "status");
    }

    // ------------------------------------------------------------------
    // Scenario de test
    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Recherche Casablanca -> Paris (2026-11-15)");
        for (String f : searchFlights("Casablanca", "Paris", "2026-11-15")) {
            System.out.println("   " + f);
        }

        System.out.println("== 2. Chemin nominal : hold -> confirm");
        String holdId = holdFlight("AIR-CMN-ORY-1330", 1);
        System.out.println("   holdId     = " + holdId);
        String bookingRef = confirmFlight(holdId);
        System.out.println("   bookingRef = " + bookingRef);

        System.out.println("== 3. Compensation : cancel (rejoue 2 fois -> idempotent)");
        System.out.println("   cancel #1 -> " + cancelFlight(bookingRef));
        System.out.println("   cancel #2 -> " + cancelFlight(bookingRef));

        System.out.println("== 4. Echec voulu : 4 places sur un vol qui n'en a que 3");
        try {
            holdFlight("AIR-CMN-MAD-1800", 4);
            System.out.println("   ??? aucune erreur : inattendu");
        } catch (SoapFault e) {
            System.out.println("   OK, fault recue : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Plomberie SOAP/XML minimale
    // ------------------------------------------------------------------

    /** Enveloppe le corps, envoie en HTTP POST, renvoie le XML de reponse. */
    static String call(String bodyXml) throws Exception {
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:air=\"" + NS + "\">"
                + "<soapenv:Header/><soapenv:Body>" + bodyXml + "</soapenv:Body>"
                + "</soapenv:Envelope>";
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();
        if (body.contains("Fault>")) {
            String reason = tag(body, "faultstring");
            throw new SoapFault(reason.isEmpty() ? body : reason);
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " : " + body);
        }
        return body;
    }

    /** Contenu de la premiere balise <name>...</name> (prefixe de namespace ignore). */
    static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<(?:\\w+:)?" + name + "(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?" + name + ">",
                Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Contenu de toutes les balises <name>...</name> (liste). */
    static List<String> allBlocks(String xml, String name) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("<(?:\\w+:)?" + name + "(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?" + name + ">",
                Pattern.DOTALL).matcher(xml);
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
