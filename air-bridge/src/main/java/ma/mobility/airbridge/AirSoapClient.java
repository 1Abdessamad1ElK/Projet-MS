package ma.mobility.airbridge;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Seule classe qui connait SOAP/XML. Les messages respectent le contrat fourni
 * (air.xsd, namespace http://mobility.ma/air). Traduit Flight -> Offer et
 * SOAP Fault -> NotAvailableException.
 *
 * Choix (ADR-005) : enveloppes SOAP construites a partir de l'XSD + JDK HttpClient,
 * plutot que du code genere (wsimport/JAXB) : zero etape de generation, build fiable.
 * Limite assumee : si le WSDL change, il faut mettre a jour ces gabarits a la main.
 */
@Component
public class AirSoapClient {

    private static final Logger log = LoggerFactory.getLogger(AirSoapClient.class);
    private static final String NS = "http://mobility.ma/air";
    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("air-bridge");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String url;

    public AirSoapClient(@Value("${air.soap-url}") String url) {
        this.url = url;
    }

    // ------------------------------------------------------------------
    // Operations du modele canonique
    // ------------------------------------------------------------------

    public List<Api.Offer> search(String origin, String destination, String date) {
        return traced("searchFlights", () -> {
            Document doc = call("<air:searchFlightsRequest>"
                    + "<air:origin>" + esc(origin) + "</air:origin>"
                    + "<air:destination>" + esc(destination) + "</air:destination>"
                    + "<air:date>" + esc(date) + "</air:date>"
                    + "</air:searchFlightsRequest>");
            List<Api.Offer> offers = new ArrayList<>();
            NodeList flights = doc.getElementsByTagNameNS(NS, "flight");
            for (int i = 0; i < flights.getLength(); i++) {
                Element f = (Element) flights.item(i);
                offers.add(new Api.Offer(
                        text(f, "flightId"), "FLIGHT", text(f, "origin"), text(f, "destination"),
                        text(f, "departure"), text(f, "arrival"),
                        Long.parseLong(text(f, "priceMinor")), "MAD",
                        Integer.parseInt(text(f, "seatsAvailable"))));
            }
            return offers;
        });
    }

    public String hold(String flightId, int seats) {
        return traced("holdFlight", () -> text(call("<air:holdFlightRequest>"
                + "<air:flightId>" + esc(flightId) + "</air:flightId>"
                + "<air:seats>" + seats + "</air:seats>"
                + "</air:holdFlightRequest>").getDocumentElement(), "holdId"));
    }

    public String confirm(String holdId) {
        return traced("confirmFlight", () -> text(call("<air:confirmFlightRequest>"
                + "<air:holdId>" + esc(holdId) + "</air:holdId>"
                + "</air:confirmFlightRequest>").getDocumentElement(), "bookingRef"));
    }

    /** Idempotent cote partenaire : on peut rejouer. */
    public void cancel(String reference) {
        traced("cancelFlight", () -> {
            call("<air:cancelFlightRequest>"
                    + "<air:reference>" + esc(reference) + "</air:reference>"
                    + "</air:cancelFlightRequest>");
            return Boolean.TRUE;
        });
    }

    // ------------------------------------------------------------------
    // Plomberie SOAP
    // ------------------------------------------------------------------

    private Document call(String bodyXml) {
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:air=\"" + NS + "\"><soapenv:Header/><soapenv:Body>"
                + bodyXml + "</soapenv:Body></soapenv:Envelope>";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            Document doc = parse(resp.body());
            if (doc.getElementsByTagNameNS("*", "Fault").getLength() > 0) {
                NodeList fs = doc.getElementsByTagNameNS("*", "faultstring");
                String reason = fs.getLength() > 0 ? fs.item(0).getTextContent().trim() : "SOAP Fault";
                throw new Errors.NotAvailableException(reason);
            }
            if (resp.statusCode() != 200) {
                throw new Errors.UpstreamUnavailableException("HTTP " + resp.statusCode() + " du partenaire aerien", null);
            }
            return doc;
        } catch (IOException e) {
            throw new Errors.UpstreamUnavailableException("Partenaire aerien injoignable : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Errors.UpstreamUnavailableException("Appel interrompu", e);
        } catch (Errors.NotAvailableException | Errors.UpstreamUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new Errors.UpstreamUnavailableException("Reponse SOAP inexploitable : " + e.getMessage(), e);
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // anti-XXE
        f.setXIncludeAware(false);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Texte du premier descendant <local> (n'importe quel prefixe de namespace). */
    private static String text(Node parent, String local) {
        NodeList nl = parent instanceof Document
                ? ((Document) parent).getElementsByTagNameNS("*", local)
                : ((Element) parent).getElementsByTagNameNS("*", local);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent().trim();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ------------------------------------------------------------------
    // Span OpenTelemetry manuel autour de l'appel SOAP
    // ------------------------------------------------------------------

    private <T> T traced(String method, Supplier<T> body) {
        Span span = TRACER.spanBuilder("AirPartner/" + method)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "soap")
                .setAttribute("rpc.service", "AirPartner")
                .setAttribute("rpc.method", method)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return body.get();
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, String.valueOf(e.getMessage()));
            log.debug("SOAP {} en erreur : {}", method, e.toString());
            throw e;
        } finally {
            span.end();
        }
    }
}
