package ma.mobility.railbridge;

import mobility.rail.NotAvailable;
import mobility.rail.RailBooking;
import mobility.rail.RailBookingHelper;
import mobility.rail.Trip;

import org.omg.CORBA.ORB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Client CORBA : seule classe qui connait IIOP, l'ORB et les types de l'IDL.
 * Elle traduit Trip -> Offer (modele canonique) et NotAvailable -> NotAvailableException.
 *
 * Bootstrap : lecture de l'IOR publie par le serveur (fichier partage).
 * Si le serveur redemarre (nouvel IOR), l'appel echoue puis la reference est
 * rechargee au prochain appel (reset()).
 */
@Component
public class RailCorbaClient {

    private static final Logger log = LoggerFactory.getLogger(RailCorbaClient.class);

    private final String iorFile;
    private ORB orb;
    private RailBooking stub;

    public RailCorbaClient(@Value("${rail.ior-file}") String iorFile) {
        this.iorFile = iorFile;
    }

    // ------------------------------------------------------------------
    // Operations du modele canonique
    // ------------------------------------------------------------------

    public List<Api.Offer> search(final String origin, final String destination, final String date) {
        return Traced.run("search", () -> {
            try {
                Trip[] trips = stub().search(origin, destination, date);
                List<Api.Offer> out = new ArrayList<Api.Offer>();
                for (Trip t : trips) {
                    out.add(toOffer(t));
                }
                return out;
            } catch (org.omg.CORBA.SystemException e) {
                throw unreachable(e);
            }
        });
    }

    public String hold(final String tripId, final int seats) {
        return Traced.run("hold", () -> {
            try {
                return stub().hold(tripId, seats);
            } catch (NotAvailable e) {
                throw new Errors.NotAvailableException(e.reason);
            } catch (org.omg.CORBA.SystemException e) {
                throw unreachable(e);
            }
        });
    }

    public String confirm(final String holdId) {
        return Traced.run("confirm", () -> {
            try {
                return stub().confirm(holdId);
            } catch (NotAvailable e) {
                throw new Errors.NotAvailableException(e.reason);
            } catch (org.omg.CORBA.SystemException e) {
                throw unreachable(e);
            }
        });
    }

    /** Idempotent cote serveur : on peut rejouer sans risque. */
    public void cancel(final String reference) {
        Traced.run("cancel", () -> {
            try {
                stub().cancel(reference);
                return Boolean.TRUE;
            } catch (org.omg.CORBA.SystemException e) {
                throw unreachable(e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Traduction Trip (CORBA) -> Offer (canonique)
    // ------------------------------------------------------------------

    private static Api.Offer toOffer(Trip t) {
        Api.Offer o = new Api.Offer();
        o.offerId = t.tripId;
        o.mode = "TRAIN";
        o.origin = t.origin;
        o.destination = t.destination;
        o.departure = t.departure;
        o.arrival = t.arrival;
        o.priceMinor = t.priceMinor;
        o.currency = "MAD";
        o.seatsAvailable = t.seatsAvailable;
        return o;
    }

    // ------------------------------------------------------------------
    // ORB / reference
    // ------------------------------------------------------------------

    private synchronized RailBooking stub() {
        if (stub == null) {
            try {
                String ior = new String(Files.readAllBytes(Paths.get(iorFile)), StandardCharsets.UTF_8).trim();
                if (orb == null) {
                    Properties props = new Properties();
                    props.setProperty("org.omg.CORBA.ORBClass", "org.jacorb.orb.ORB");
                    props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
                    orb = ORB.init(new String[0], props);
                }
                stub = RailBookingHelper.narrow(orb.string_to_object(ior));
                log.info("Reference CORBA chargee depuis {}", iorFile);
            } catch (IOException e) {
                throw new Errors.UpstreamUnavailableException("IOR illisible (" + iorFile + ") : " + e.getMessage());
            } catch (RuntimeException e) {
                throw new Errors.UpstreamUnavailableException("ORB/IOR invalide : " + e);
            }
        }
        return stub;
    }

    private synchronized void reset() {
        stub = null;
    }

    private Errors.UpstreamUnavailableException unreachable(org.omg.CORBA.SystemException e) {
        reset();
        log.warn("Operateur ferroviaire injoignable : {}", e.toString());
        return new Errors.UpstreamUnavailableException("Operateur ferroviaire injoignable : " + e.getClass().getSimpleName());
    }

    @PreDestroy
    void shutdown() {
        if (orb != null) {
            orb.shutdown(false);
        }
    }
}
